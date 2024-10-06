import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class IntermediateCodeVisitor extends CompiScriptBaseVisitor<Object> {

    private String CurrClasName = "";
    private String CurrFatherCall = "";
    // Simple expressions
    private int tempCounter = 0;
    private int labelCount = 0; // Unique label counter
    private int tabCounter = 0;
    private int pointerCounter = 0;
    // Generates a new temporary variable for expressions
    private String newTemp() {
        return "T_" + (tempCounter++);
    }

    //the symbol table fused
    private HashMap<String, Map<String,Object>> ST ;

    public IntermediateCodeVisitor(HashMap<String, Map<String,Object>> fusedSymbolTable){
        this.ST = fusedSymbolTable;
    }
    // Generates a pointer used for referencing an attribute of a class
    // is a place holder for "retrieving the memory direction"
    private String newPointer(){
        return "P_" + (pointerCounter++);
    }

    // Helper function to generate TAC for an if condition
    private String generateConditionTAC(String condition, String labelFalse) {
        return "ifFalse " + condition + " goto " + labelFalse;
    }

    private List<String> instructions = new ArrayList<>();  // To store TAC instructions

    Pattern InstanceRegex = Pattern.compile("Instance@[0-9a-z]+");
    // Method to get the generated TAC instructions
    public List<String> getInstructions() {
        return instructions;
    }

    private String generateLabel() {
        return "L_" + (labelCount++);
    }

    // Method to write TAC instructions to a file
    public void writeToFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (String instruction : this.instructions) {
                writer.write(instruction);
                writer.newLine(); // Add a newline after each instruction
            }
            System.out.println("TAC instructions have been written to " + filePath);
        } catch (IOException e) {
            System.err.println("Error writing to file: " + e.getMessage());
        }
    }

    // Returning the primaries:
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
        if (ctx.NUMBER() != null) {
            return ctx.NUMBER().getText();  // Return the number
        } else if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();  // Return the variable name
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());  // Parenthesized expression
        } else if (ctx.STRING() != null) {
            return ctx.STRING().getText();
        }else if (ctx.superCall() != null){
            return CurrFatherCall + "::" + ctx.superCall().IDENTIFIER().getText();
        } else if (ctx.getText().equals("this")) {
            return "this";
        }else if (ctx.instantiation() != null){
            return visit(ctx.instantiation());
        }
        return "";
    }

    // Arithmetic (term) +, -
    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        String result = String.valueOf(visit(ctx.factor(0)));
        // If there are additional factors with '+' or '-' operations
        for (int i = 1; i < ctx.factor().size(); i++) {
            String nextFactor = String.valueOf(visit(ctx.factor(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '+' or '-'
            // Generate TAC for the operation
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) +temp + ":= " + result + " " + op + " " + nextFactor);
            result = temp;  // The result becomes the new temporary variable
        }

        return result;
    }

    // Visit factor: handles '*', '/', '%'
    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        // Visit the first unary
        String result = String.valueOf(visit(ctx.unary(0)));

        // If there are additional unaries with '*', '/', '%' operations
        for (int i = 1; i < ctx.unary().size(); i++) {
            String nextUnary = String.valueOf(visit(ctx.unary(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '*', '/', '%'

            // Generate TAC for the operation
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) +temp + ":= " + result + " " + op + " " + nextUnary);
            result = temp;  // The result becomes the new temporary variable
        }

        return result;
    }

    @Override
    public Object visitUnary(CompiScriptParser.UnaryContext ctx) {
        if (ctx.getChildCount() == 2) {
            // Unary operation: either '!' or '-'
            String op = ctx.getChild(0).getText();
            String operand = String.valueOf(visit(ctx.unary()));

            // Generate TAC for the unary operation
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) +temp + ":= " + op + " " + operand);
            return temp;
        } else {
            // Otherwise, it's a call, so just visit the call
            return visit(ctx.call());
        }
    }

    private String currentInstanceName = "";
    // Visit var assignment
    @Override
    public Object visitVarDecl(CompiScriptParser.VarDeclContext ctx) {
        // Capturamos el nombre de la variable que se está declarando
        String varName = ctx.IDENTIFIER().getText();
        currentInstanceName = varName;
        Object val = visit(ctx.expression());
        // Si es una instanciación, visitamos la expresión para capturar la instancia
        Matcher matcher = InstanceRegex.matcher(String.valueOf(val));
        if(!(matcher.matches())) {
            if (ctx.expression() != null) {
                instructions.add("\t".repeat(tabCounter) + varName + ":= " + val);
            } else {
                instructions.add("\t".repeat(tabCounter) + "ALLOC " + varName);
            }
        }
        currentInstanceName = "";
        return null;
    }
    public Object visitAssignment(CompiScriptParser.AssignmentContext ctx){
        if(ctx.logic_or() != null) {
            return visit(ctx.logic_or());
        } else if (ctx.call() != null) {
            //get a pointer into a parameter
            //add the instruction of retrieving it
            if(Objects.equals(ctx.call().getText(), "this")){
                String pointer = newPointer();
                instructions.add("\t".repeat(tabCounter) + "LOAD "  + pointer + " " +"SELF" + " " + ctx.IDENTIFIER().getText());
                //get the new expression
                String val = String.valueOf(visit(ctx.assignment()));
                instructions.add("\t".repeat(tabCounter) + pointer + ":= " + val);
            }
        }else{
            //get the new expression
            String name = ctx.IDENTIFIER().getText();
            String val = String.valueOf(visit(ctx.assignment()));
            instructions.add("\t".repeat(tabCounter) + name + ":= " + val);
        }
        return null;
    }
    // Visit if statement
    @Override
    public Object visitIfStmt(CompiScriptParser.IfStmtContext ctx) {
        // Generate labels for true block, false block, and end
        String labelElse = generateLabel();
        String labelEnd = generateLabel();

        // Visit the condition expression
        String condition = String.valueOf(visit(ctx.expression()));

        // Generate TAC for the condition

        instructions.add("\t".repeat(tabCounter) +generateConditionTAC(condition, labelElse));
        tabCounter++;
        // Visit the 'if' block (true case)
        visit(ctx.statement(0));  // The first statement is the 'if' body

        // Jump to end if true
        instructions.add("\t".repeat(tabCounter) + "goto " + labelEnd);
        tabCounter--;
        // False block (else, if present)
        instructions.add("\t".repeat(tabCounter) + labelElse + ":");
        if (ctx.statement(1) != null) {
            tabCounter++;
            visit(ctx.statement(1));  // The second statement is the 'else' body
            tabCounter--;
        }

        // End label
        instructions.add("\t".repeat(tabCounter) + labelEnd + ":");

        return null;
    }

    // Visit while loop
    @Override
    public String visitWhileStmt(CompiScriptParser.WhileStmtContext ctx) {
        // Generate labels
        String startLabel = generateLabel();
        String endLabel = generateLabel();

        // Start of the while loop
        instructions.add("\t".repeat(tabCounter) + startLabel + ":");

        // Visit condition and generate condition TAC
        String condition = String.valueOf(visit(ctx.expression()));
        instructions.add("\t".repeat(tabCounter) +generateConditionTAC(condition, endLabel));
        tabCounter ++;
        // Visit loop body
        visit(ctx.statement());
        tabCounter --;
        // Jump back to start to recheck condition
        instructions.add("\t".repeat(tabCounter) + "goto " + startLabel);

        // End of the loop
        instructions.add("\t".repeat(tabCounter) + endLabel + ":");

        return null;
    }

    // Visit for loop
    @Override
    public String visitForStmt(CompiScriptParser.ForStmtContext ctx) {
        // Generate labels
        String startLabel = generateLabel();
        String endLabel = generateLabel();

        // Visit initialization
        if (ctx.varDecl() != null) {
            visit(ctx.varDecl()); // Correct initialization
        } else if (ctx.exprStmt() != null) {
            visit(ctx.exprStmt());
        }

        // Start of the loop
        instructions.add("\t".repeat(tabCounter) + startLabel + ":");

        // Visit the condition expression (if present)
        if (ctx.expression(0) != null) {
            String exprResult = String.valueOf(visit(ctx.expression(0)));
            instructions.add(generateConditionTAC(exprResult, endLabel));
        }

        // Visit loop body
        tabCounter++;
        visit(ctx.statement());
        tabCounter--;
        // Visit the increment expression (if present)
        if (ctx.expression(1) != null) {
            visit(ctx.expression(1)); // Visit increment statement
        }

        // Jump back to condition
        instructions.add("\t".repeat(tabCounter) + "goto " + startLabel);

        // End of the loop
        instructions.add("\t".repeat(tabCounter) + endLabel + ":");

        return null;
    }

    // Visit class declaration
    @Override
    public Object visitClassDecl(CompiScriptParser.ClassDeclContext ctx) {
        CurrClasName = ctx.IDENTIFIER(0).getText();
        String className = ctx.IDENTIFIER(0).getText();
        CurrFatherCall = ctx.IDENTIFIER().size() > 1 ? ctx.IDENTIFIER().get(1).toString() : "";

        // Visit all the functions in the class
        for (CompiScriptParser.FunctionContext functionCtx : ctx.function()) {
            visit(functionCtx);
        }
        CurrClasName = "";
        CurrFatherCall = "";
        return null;
    }

    // Visit function declaration
    @Override
    public Object visitFunction(CompiScriptParser.FunctionContext ctx) {

        String functionName = ctx.IDENTIFIER().getText();

        if(!CurrClasName.isEmpty()){
            instructions.add("\t".repeat(tabCounter) + "FUN " + CurrClasName + "::" + functionName);
        }else{
            instructions.add("\t".repeat(tabCounter) + "FUN " + functionName);
        }

        //add the parameters
        tabCounter ++;
        if(!CurrClasName.isBlank()){
            instructions.add("\t".repeat(tabCounter) + "PARAM SELF");
        }
        if (ctx.parameters() != null){
            for (int i = 0; i < ctx.parameters().getChildCount(); i+=2){
                String param = String.valueOf(ctx.parameters().getChild(i).getText());
                instructions.add("\t".repeat(tabCounter) + "PARAM " + param);
            }
        }

        // Visit the function body (block)

        visit(ctx.block());
        tabCounter --;
        instructions.add("\t".repeat(tabCounter) +"ENDFUN");
        return null;
    }


    @Override
    public Object visitCall(CompiScriptParser.CallContext ctx) {
        // Verificamos si estamos trabajando con una instancia de "new"
        if (ctx.getChildCount() == 1) { //primary call
            if (ctx.primary() != null) {
                if (ctx.primary().array() != null) { // is an array (somehow)
                    return visit(ctx.primary().array());
                } else { //just a primary
                    return visit(ctx.primary());
                }
            }
        }

        // Si no es una instancia de "new", manejamos una llamada regular
        String primary = String.valueOf(visit(ctx.primary()));
        if(primary.equals("this")){
            //return the pointer that will have the information loaded
            String pointer = newPointer();
            instructions.add("\t".repeat(tabCounter) + "LOAD "  + pointer + " " +"SELF"+ " " + ctx.IDENTIFIER().getFirst().getText());
            return pointer;
        }
        if (ST.containsKey(primary)) {
            Object typePrimary = ST.get(primary).getOrDefault("type", null);
            if (typePrimary == null) return null;
            if(typePrimary instanceof  Function){
                // Si hay argumentos en la llamada
                if(ctx.arguments() != null && !ctx.arguments().isEmpty()) {
                    CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                    for (int i = 0; i < arguments.getChildCount(); i += 2) {
                        String arg = String.valueOf(visit(arguments.getChild(i)));
                        instructions.add("\t".repeat(tabCounter) + "PUSH " + arg);
                    }
                }
                instructions.add("\t".repeat(tabCounter) + "CALL "+ primary);
            }
            else if (typePrimary instanceof Instance) {
                Object lastDeclaration = (Instance) ST.get(primary).get("type"); //contiene info del nombre de la variable que es la instancia;
                int i = 1;
                while (i < ctx.getChildCount()) {
                    if (ctx.getChild(i).getText().equals(".") ||
                            ctx.getChild(i).getText().equals(")") ||
                            ctx.getChild(i).getText().equals("[") ||
                            ctx.getChild(i).getText().equals("(") ||
                            ctx.getChild(i).getText().equals("]")
                    ) {//the Identifier is the next one so continue
                        i++;
                        continue;
                    }
                    if (lastDeclaration instanceof Instance) {
                        //get the next Identifier
                        String attr = ((Instance) lastDeclaration).getLookUpName() +
                                "." + ctx.getChild(i).getText();
                        String method = ((Instance) lastDeclaration).getClasName() +
                                "." + ctx.getChild(i).getText();
                        if (ST.containsKey(attr)) { // is an attribute
                            lastDeclaration = ST.get(attr).get("type");
                            //load it into a pointer
                            String pointer = newPointer();
                            instructions.add(
                                    "\t".repeat(tabCounter) + "LOAD "
                                            + pointer + " " + attr.replace("."," "));
                        } else if (ST.containsKey(method)) {//is a method

                            instructions.add("\t".repeat(tabCounter) + "PUSH " + ((Instance) lastDeclaration).getLookUpName());
                            Method methodObj = (Method) ST.get(method).get("type");
                            String methodName = ctx.getChild(i).getText();
                            i += 2; //skip the opening brackets
                            while (!ctx.getChild(i).getText().equals(")")) {
                                String arg = String.valueOf(visit(ctx.getChild(i)));
                                instructions.add("\t".repeat(tabCounter) + "PUSH " + arg);
                                i++;
                            }
                            instructions.add("\t".repeat(tabCounter) +
                                    "CALL " + method.replace(".", "::")
                            );
                            //lastDeclaration = visitChildren(((Method)ST.get(method).get("type")).getCtx());
                        }
                    }
                    i++;
                }
            }
        }
        return null;
    }
    //new instance
    @Override
    public Object visitInstantiation(CompiScriptParser.InstantiationContext ctx) {
        // Obtenemos el nombre de la clase que estamos instanciando
        String className = ctx.IDENTIFIER().getText();

        // Usamos el nombre de la instancia almacenado en currentInstanceName
        String instanceName = currentInstanceName;
        // Generamos la instrucción de ALLOCATE para reservar memoria para el objeto
        instructions.add("\t".repeat(tabCounter) + "ALLOC " + instanceName);
        instructions.add("\t".repeat(tabCounter) + "PUSH " + instanceName);
        // Manejamos los argumentos (parámetros pasados al constructor)

        if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {
            CompiScriptParser.ArgumentsContext arguments = ctx.arguments();  // Primer set de argumentos
            for (int i = 0; i < arguments.getChildCount(); i += 2) {  // Itera sobre los argumentos
                String arg = String.valueOf(visit(arguments.getChild(i)));
                instructions.add("\t".repeat(tabCounter) + "PUSH " + arg);  // Pushea cada argumento
            }
        }

        // Llamada al constructor con la clase especificada
        instructions.add("\t".repeat(tabCounter) + "CALL " + className + "::init");
        return new Instance("",className);
    }
    // Visit comparison (==, !=, >, <, >=, <=)
    @Override
    public Object visitEquality(CompiScriptParser.EqualityContext ctx) {
        String left = String.valueOf(visit(ctx.comparison(0)));  // Visit the left side of the comparison
        String result = left;

        // If there are multiple comparisons
        for (int i = 1; i < ctx.comparison().size(); i++) {
            String right = String.valueOf(visit(ctx.comparison(i)));
            String operator = ctx.getChild(2 * i - 1).getText(); // '==', '!=', '>', '<', etc.

            // Generate TAC for the comparison operation
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + left + " " + operator + " " + right);
            result = temp;  // The result becomes the new temporary variable
        }

        return result;
    }

    @Override
    public Object visitComparison(CompiScriptParser.ComparisonContext ctx) {
        String left = String.valueOf(visit(ctx.term(0)));  // Visit the left side of the comparison
        String result = left;

        // If there are multiple comparisons
        for (int i = 1; i < ctx.term().size(); i++) {
            String right = String.valueOf(visit(ctx.term(i)));
            String operator = ctx.getChild(2 * i - 1).getText(); // '==', '!=', '>', '<', etc.

            // Generate TAC for the comparison operation
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + left + " " + operator + " " + right);
            result = temp;  // The result becomes the new temporary variable
        }

        return result;
    }

    // Visit logic OR
    @Override
    public Object visitLogic_or(CompiScriptParser.Logic_orContext ctx) {
        String result = String.valueOf(visit(ctx.logic_and(0)));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String nextComparison = String.valueOf(visit(ctx.logic_and(i + 1)));
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " || " + nextComparison);
            result = temp;
        }
        return result;
    }

    // Visit logic AND
    @Override
    public Object visitLogic_and(CompiScriptParser.Logic_andContext ctx) {
        String result = String.valueOf(visit(ctx.equality(0)));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String nextComparison = String.valueOf(visit(ctx.equality(i + 1)));
            String temp = newTemp();
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " && " + nextComparison);
            result = temp;
        }
        return result;
    }

    //IO
    @Override
    public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
        String io = String.valueOf(visit(ctx.expression()));
        instructions.add("\t".repeat(tabCounter) + "OUT"+ " " + io);
        return null;
    }
}
