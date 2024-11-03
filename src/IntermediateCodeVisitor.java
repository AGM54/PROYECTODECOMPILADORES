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
    private Stack<String> LabelStack = new Stack<>();
    private String inverseLabel = "";
    // Generates a new temporary variable for expressions
    private Stack<String> tempPool = new Stack<>();
    private HashMap<String,Object> tmpTalbe = new HashMap<>();


    private List<String> jumpCalls = new ArrayList<>();  // To store functions blocks
    private List<String> mainCalls = new ArrayList<>();  // To store TAC instructions
    private List<String> instructions = mainCalls;  // To store TAC instructions

    //the symbol table fused
    private HashMap<String, Map<String,Object>> ST ;
    private HashMap<String, Map<String,Object>> FT ;
    private HashMap<String, Map<String,Object>> CT ;
    private HashMap<String, Map<String,Object>> PT ;
    private Boolean inFunction = false;
    private String newTemp(Object val) {
        String tmp;
        if (!tempPool.isEmpty()){
            tmp = tempPool.pop();
        }else{
            tmp = "$t" + (tempCounter++);
        }
        tmpTalbe.put(tmp,val);
        return tmp;
    }

    private void releaseTemp(String temp) {
        tmpTalbe.put(temp,null);
        tempPool.push(temp);  // Return the temporary to the pool when done
    }

    private Object getTempVal(String temp){
        return tmpTalbe.get(temp);
    }


    private List<Map<String,Map<String,Object>>> search (String _search){
        Pattern regex = Pattern.compile(_search); // Compile the regex pattern
        List<Map<String,Map<String,Object>>> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : this.CT.entrySet()) {
            String keyName = entry.getKey(); // First key
            if (regex.matcher(keyName).find()){
                results.add(new HashMap<>(){{put(entry.getKey(),entry.getValue());}});
            }
        }
        return results;
    };
    private void addVar (String name, Map<String,Object> value){
        if(CT.containsKey(name.split("\\.")[0])){
            return;
        }
        Object initialValue = value.get("type");
        String javaType =  value.get("type").getClass().getSimpleName();
        if(value.get("type") instanceof Instance){
            String className = ((Instance) value.get("type")).getClasName();
            for(Map<String, Map<String, Object>> entry : search(className)){
                for (String key : entry.keySet()) {
                    Map<String, Object> innerMap = entry.get(key);
                    addVar(key.replace(className+".",((Instance) value.get("type")).getLookUpName() + "_"),innerMap);
                    // Access values from the inner map using the key
                }
            };
        } else if (value.get("type") instanceof Param || value.get("type") instanceof Class || value.get("type") instanceof Method || value.get("type") instanceof Function) {
            return;
        }else {

            switch (javaType) {
                case "Integer":
                    instructions.add(name + ": .word " + initialValue);
                    return;
                case "Float":
                    instructions.add(name + ": .float " + initialValue);
                    return;
                case "Double":
                    instructions.add(name + ": .double " + initialValue);
                    return;
                case "Character":
                    instructions.add(name + ": .byte '" + initialValue + "'");
                    return;
                case "Boolean":
                    instructions.add(name + ": .byte " + ((Boolean) initialValue ? "1" : "0"));
                    return;
                case "String":
                    instructions.add(name + ": .asciiz \"" + initialValue + "\"");
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + javaType);
            }
        }
    }
    public IntermediateCodeVisitor(HashMap<String, Map<String,Object>> fusedSymbolTable,
                                   HashMap<String, Map<String,Object>> fusedFunctionsTable,
                                   HashMap<String, Map<String,Object>> fusedClassesTable,
                                   HashMap<String, Map<String,Object>> fusedParametersTable){
        this.ST = fusedSymbolTable;
        this.FT = fusedFunctionsTable;
        this.CT = fusedClassesTable;
        this.PT = fusedParametersTable;
        if(!ST.isEmpty()){
            instructions.add(".data");
            ST.forEach(this::addVar);
            instructions.add(".text");
            instructions.add(".globl main");
            instructions.add("main:");
        }
    }
    // Generates a pointer used for referencing an attribute of a class
    // is a place holder for "retrieving the memory direction"
    private String newPointer(){
        return "P_" + (pointerCounter++);
    }

    // Helper function to generate TAC for an if condition

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
            this.mainCalls.addAll(jumpCalls);
            for (String instruction : this.mainCalls) {
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
        // Check if there's only one factor; no need for temporary
        if (ctx.factor().size() == 1) {
            return visit(ctx.factor(0));
        }

        String result = String.valueOf(visit(ctx.factor(0)));
        for (int i = 1; i < ctx.factor().size(); i++) {
            String nextFactor = String.valueOf(visit(ctx.factor(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '+' or '-'
            String R = "", N = "";
            if (result.startsWith("$t")) {
                R = result;
                result = String.valueOf( getTempVal(result) );
                releaseTemp(R);
            }
            if (nextFactor.startsWith("$t")) {
                N = nextFactor;
                nextFactor = String.valueOf( getTempVal(nextFactor) );
                releaseTemp(N);
            }

            if (ST.containsKey(result)){
                R = result;
                result = String.valueOf(ST.get(result).get("type"));

            } else if (PT.containsKey(result) && inFunction) {
                R = result;
                Object v = PT.get(result).get("type");
                result = String.valueOf(((Param) v).getTypeInstnce());
            }
            if (ST.containsKey(nextFactor)){
                N = nextFactor;
                nextFactor = String.valueOf(ST.get(nextFactor).get("type"));
            }else if (PT.containsKey(nextFactor) && inFunction) {
                N = nextFactor;
                Object v = PT.get(nextFactor).get("type");
                nextFactor = String.valueOf(((Param) v).getTypeInstnce());
            }
            // Generate a new temporary register for the result
            String temp = "";

            // Translate '+' and '-' into MIPS 'add' and 'sub' instructions
            switch (op) {
                case "+":
                    if (result.startsWith("\"") || nextFactor.startsWith("\"") ){
                        temp = newTemp( result + nextFactor);
                        result = R.isBlank()? result : R;
                        nextFactor = N.isBlank()? nextFactor : N;
                        instructions.add("\t".repeat(tabCounter) + "concat " + temp + ", " + result + ", " + nextFactor);
                    }else {
                        temp = newTemp(Double.parseDouble(result)  + Double.parseDouble(nextFactor));
                        result = R.isBlank()? result : R;
                        nextFactor = N.isBlank()? nextFactor : N;
                        instructions.add("\t".repeat(tabCounter) + "add " + temp + ", " + result + ", " + nextFactor);
                    }
                    break;
                case "-":
                    temp = newTemp(Double.parseDouble(result )  - Double.parseDouble(nextFactor));
                    result = R.isBlank()? result : R;
                    nextFactor = N.isBlank()? nextFactor : N;
                    instructions.add("\t".repeat(tabCounter) + "sub " + temp + ", " + result + ", " + nextFactor);
                    break;
            }
            // Update the result to the current temporary
            result = temp;
        }

        // Release the final temporary register after use if necessary
        if (result.startsWith("$t")) {
            releaseTemp(result);
        }

        return result;
    }

    // Visit factor: handles '*', '/', '%'
    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        // Check if there's only one unary; no need for temporary
        if (ctx.unary().size() == 1) {
            return visit(ctx.unary(0));
        }

        String result = String.valueOf(visit(ctx.unary(0)));
        for (int i = 1; i < ctx.unary().size(); i++) {
            String nextUnary = String.valueOf(visit(ctx.unary(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '*', '/', '%'

            // Generate a new temporary register for the result
            String temp = newTemp(0);
            // Translate '*', '/', '%' into MIPS 'mul', 'div', and modulus instructions
            switch (op) {
                case "*":
                    instructions.add("\t".repeat(tabCounter) + "mul " + temp + ", " + result + ", " + nextUnary);
                    break;
                case "/":
                    // MIPS division: quotient is stored in $lo, remainder in $hi
                    instructions.add("\t".repeat(tabCounter) + "div " + result + ", " + nextUnary);
                    instructions.add("\t".repeat(tabCounter) + "mflo " + temp); // Move quotient to temp
                    break;
                case "%":
                    // MIPS modulus: use the remainder from division, which is in $hi
                    instructions.add("\t".repeat(tabCounter) + "div " + result + ", " + nextUnary);
                    instructions.add("\t".repeat(tabCounter) + "mfhi " + temp); // Move remainder to temp
                    break;
            }
            if (result.startsWith("$t")) {
                releaseTemp(result);
            }
            // Update the result to the current temporary
            result = temp;

        }

        // Release the final temporary register after use if necessary
        if (result.startsWith("$t")) {
            releaseTemp(result);
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
            String temp = newTemp(op + operand);
            instructions.add("\t".repeat(tabCounter) + "li" + temp +" " + op + operand);
            releaseTemp(temp);
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
                instructions.add("\t".repeat(tabCounter) + "li" + " " + varName + " " + val);
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
                instructions.add("\t".repeat(tabCounter) + "li" + " " + pointer + " " + val);
            }
        }else{
            //get the new expression
            String name = ctx.IDENTIFIER().getText();
            String val = String.valueOf(visit(ctx.assignment()));
            instructions.add("\t".repeat(tabCounter) + "li" + " " + name + " " + val);
        }
        return null;
    }
    // Visit if statement
    @Override
    public Object visitIfStmt(CompiScriptParser.IfStmtContext ctx) {
        // Generate labels for true block, false block, and end
        String labelTrue = generateLabel();
        String labelElse = "";
        String labelEnd = generateLabel();

        // Visit the condition expression
        LabelStack.push(labelTrue);

        if (ctx.statement(1) != null) {
            labelElse = generateLabel();
            inverseLabel = labelElse;
        }else{
            inverseLabel = labelEnd;
        }

        String condition = String.valueOf(visit(ctx.expression()));
        inverseLabel="";
        LabelStack.pop();

        // Generate TAC for the condition
        instructions.add("\t".repeat(tabCounter) + labelTrue + ":");
        tabCounter++;

        // Visit the 'if' block (true case)
        visit(ctx.statement(0));  // The first statement is the 'if' body

        // Jump to end if true
        if(!instructions.getLast().split(" ")[0].strip().stripIndent().startsWith("j")){
            instructions.add("\t".repeat(tabCounter) + "j " + labelEnd);
        }

        tabCounter--;

        if (ctx.statement(1) != null) {
            // False block (else, if present)
            instructions.add("\t".repeat(tabCounter) + labelElse + ":");
            LabelStack.push(labelElse);
            tabCounter++;
            visit(ctx.statement(1));  // The second statement is the 'else' body
            tabCounter--;
            LabelStack.pop();
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
        inverseLabel=endLabel;
        String condition = String.valueOf(visit(ctx.expression()));
        inverseLabel="";
        tabCounter ++;
        // Visit loop body
        visit(ctx.statement());
        tabCounter --;
        // Jump back to start to recheck condition
        instructions.add("\t".repeat(tabCounter) + "j " + startLabel);

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
            inverseLabel=endLabel;
            String exprResult = String.valueOf(visit(ctx.expression(0)));
            inverseLabel="";
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
        instructions.add("\t".repeat(tabCounter) + "j " + startLabel);

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
        if(ctx.block().declaration().isEmpty()){
            return null;
        }
        instructions = jumpCalls;
        if(!CurrClasName.isEmpty()){
            instructions.add("\t".repeat(tabCounter) + CurrClasName.toLowerCase() + "_" + functionName.toLowerCase() + ":");
        }else{
            instructions.add("\t".repeat(tabCounter)  + functionName.toLowerCase() + ":");
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
        inFunction = true;
        visit(ctx.block());
        inFunction = false;
        instructions.add("\t".repeat(tabCounter) +"jr" +" " +"$ra");
        tabCounter --;
        instructions = mainCalls;
        return null;
    }

    @Override
    public Object visitReturnStmt(CompiScriptParser.ReturnStmtContext ctx){
        Object val = null;
        if (ctx.expression() != null){
            val = String.valueOf(visit(ctx.expression()));
            instructions.add("\t".repeat(tabCounter) +"li"+" "+ "$v0" + " " + val) ;

        }
        return val;
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
                String pointer = newPointer();
                instructions.add("\t".repeat(tabCounter) + "jal "+ primary + " " + pointer);
                if(ctx.arguments() != null && !ctx.arguments().isEmpty()) {
                    CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                    for (int i = 0; i < arguments.getChildCount(); i += 2) {
                        String arg = String.valueOf(visit(arguments.getChild(i)));
                        instructions.add("\t".repeat(tabCounter) + "POP " + arg);
                    }
                }
                return pointer;
            }
            else if (typePrimary instanceof Instance) {
                Object lastDeclaration = (Instance) ST.get(primary).get("type"); //contiene info del nombre de la variable que es la instancia;
                String lastPointer  = "";
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
                            lastPointer = newPointer();
                            instructions.add(
                                    "\t".repeat(tabCounter) + "LOAD "
                                            + lastPointer + " " + attr.replace("."," "));
                        } else if (ST.containsKey(method)) {//is a method
                            //load it into a pointer
                            lastPointer = newPointer();
                            instructions.add("\t".repeat(tabCounter) + "PUSH " + ((Instance) lastDeclaration).getLookUpName());
                            Method methodObj = (Method) ST.get(method).get("type");
                            String methodName = ctx.getChild(i).getText();
                            i += 2; //skip the opening brackets
                            ArrayList<String> args = new ArrayList<>();
                            instructions.add("\t".repeat(tabCounter) + "POP " + primary);
                            while (!ctx.getChild(i).getText().equals(")")) {
                                String arg = String.valueOf(visit(ctx.getChild(i)));
                                args.add(arg);
                                instructions.add("\t".repeat(tabCounter) + "PUSH " + arg);
                                i++;
                            }
                            instructions.add("\t".repeat(tabCounter) +
                                    "jal " + method.replace(".", "_").toLowerCase() + " " + lastPointer
                            );
                            instructions.add("\t".repeat(tabCounter) + "POP " + primary);
                            args.forEach((arg) -> {
                                instructions.add("\t".repeat(tabCounter) + "POP " + arg);
                            });
                            lastDeclaration = ((Method)ST.get(method).get("type")).getReturnsType();
                        }
                    }
                    i++;
                }
                return lastPointer;
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
        instructions.add("\t".repeat(tabCounter) + "jal " + className + "::init");
        instructions.add("\t".repeat(tabCounter) + "POP " + instanceName);
        if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {
            CompiScriptParser.ArgumentsContext arguments = ctx.arguments();  // Primer set de argumentos
            for (int i = 0; i < arguments.getChildCount(); i += 2) {  // Itera sobre los argumentos
                String arg = String.valueOf(visit(arguments.getChild(i)));
                instructions.add("\t".repeat(tabCounter) + "POP " + arg);  // Pushea cada argumento
            }
        }
        return new Instance("",className);
    }
    // Visit comparison (==, !=, >, <, >=, <=)
    @Override
    public Object visitEquality(CompiScriptParser.EqualityContext ctx) {
        String left = String.valueOf(visit(ctx.comparison(0)));  // Visit the left side of the comparison
        String result = left;
        String jump = "";
        if (!LabelStack.isEmpty()){
            jump = LabelStack.peek();
        }
        // If there are multiple comparisons
        for (int i = 1; i < ctx.comparison().size(); i++) {
            String right = String.valueOf(visit(ctx.comparison(i)));
            String lastInstruction = "";
            String operator = ctx.getChild(2 * i - 1).getText(); // '==', '!='

            switch (operator){
                case "==" -> {
                    instructions.add("\t".repeat(tabCounter)  + "beq"  + " "  + left +  " " + right + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + left +  " " + right + " " + inverseLabel);
                    }
                }
                case "!=" -> {
                    instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + left +  " " + right + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "beq"  + " "  + left +  " " + right + " " + inverseLabel);
                    }
                }
            }
            if(result.startsWith("$t")) {
                releaseTemp(result);
            }
            result = lastInstruction;  // The result becomes the new temporary variable
        }
        if(result.startsWith("$t")) {
            releaseTemp(result);
        }
        return result;
    }

    @Override
    public Object visitComparison(CompiScriptParser.ComparisonContext ctx) {
        String left = String.valueOf(visit(ctx.term(0)));  // Visit the left side of the comparison
        String result = left;
        String jump = "";
        if (!LabelStack.isEmpty()){
            jump = LabelStack.peek();
        }
        // If there are multiple comparisons
        for (int i = 1; i < ctx.term().size(); i++) {
            String temp = newTemp(0);
            String right = String.valueOf(visit(ctx.term(i)));
            String operator = ctx.getChild(2 * i - 1).getText(); //'>' | '>=' | '<' | '<='

            switch (operator){
                case ">=" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt"  + " " + temp + " " +right + " " + left);
                    instructions.add("\t".repeat(tabCounter)  + "beq"  + " "  + temp + " $zero" + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " + left + " " + right + " " + inverseLabel);
                    }
                }
                case "<=" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp +  " " +  left + " " + right);
                    instructions.add("\t".repeat(tabCounter)  + "beq" + " " + temp + " $zero"  + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " +  right + " " + left  + " " + inverseLabel);
                    }
                }
                case "<" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " + left + " " + right + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " +  right + " " + left  + " " + inverseLabel);
                    }
                }
                case ">" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " +  right + " " + left  + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " + left + " " + right + " " + inverseLabel);
                    }
                }
            }
            if(result.startsWith("$t")) {
                releaseTemp(result);
            }

            result = temp;  // The result becomes the new temporary variable
        }
        if(result.startsWith("$t")) {
            releaseTemp(result);
        }
        return result;
    }

    // Visit logic OR
    @Override
    public Object visitLogic_or(CompiScriptParser.Logic_orContext ctx) {
        String result = String.valueOf(visit(ctx.logic_and(0)));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            String nextComparison = String.valueOf(visit(ctx.logic_and(i + 1)));
            String temp = newTemp(0);
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " || " + nextComparison);
            if(result.startsWith("$t")) {
                releaseTemp(result);
            }
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
            String temp = newTemp(0);
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " && " + nextComparison);
            if(result.startsWith("$t")) {
                releaseTemp(result);
            }
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
