import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;



public class EnhancedIntermediateCode extends CompiScriptBaseVisitor<Object>  {
    //auxiliary classes of dataTypes
    static class Variable {
        public String name;
        public Object value;
        public String pointer;
        public Variable(String name,Object value,String pointer){
            this.name = name;
            this.value = value;
            this.pointer = pointer;
        }
    }
    //misc

    //counters
    private int tempCounter = 0;
    private int labelCount = 0; // Unique label counter
    private int tabCounter = 0;
    private int constantCounter = 0;

    //the instructions generated
    private List<String> jumpCalls = new ArrayList<>();  //functions/methods to be called
    private List<String> mainCalls = new ArrayList<>(){{add(".text");
        add(".globl main");
        add("main:");}};  //the main flow of instructions
    private List<String> dataHeader = new ArrayList<>(){{add(".data");}};  //header for all the .data, will be used to store constant strings also
    private List<String> instructions = mainCalls;  // the pointer into where is written

    /*
    flags employed
    */
    private String CurrFatherCall = ""; // for all the super.something, to get the father.something
    private String currentInstanceName = "";
    private Stack<String> LabelStack = new Stack<>();
    private String inverseLabel = "";
    private Boolean inFunction = false;
    private String CurrClasName = "";
    private Boolean hasReturnSmt = false;
    private String CurrentFunction = "";

    //the _B_ value on .data, which is going to be a buffer to temp store strings
    private Boolean hasStringBuffer = false;

    /*
    the symbol table fused of each
    */
    private HashMap<String, Map<String,Object>> ST ; //symbols
    private HashMap<String, Map<String,Object>> FT ; //functions
    private HashMap<String, Map<String,Object>> CT ; //classes
    private HashMap<String, Map<String,Object>> PT ; //parameters
    private HashMap<String, String> StringConstants = new HashMap<>(); //string constants

    //temporals handling
    private Stack<String> tempPool = new Stack<>();
    private HashMap<String,Object> temporalsMap = new HashMap<>();

    //get a new temporal
    private String newTemp(Object val) {
        String tmp;
        if (!tempPool.isEmpty()){
            tmp = tempPool.pop();
        }else{
            tmp = "$t" + (tempCounter++);
        }
        temporalsMap.put(tmp,val);
        return tmp;
    }
    //free up a temporal
    private void releaseTemp(String temp) {
        temporalsMap.put(temp,null);
        tempPool.push(temp);  // Return the temporary to the pool when done
    }

    private Object getTempVal(String temp){
        return temporalsMap.get(temp);
    }
    //get the temporal storing that value
    private String getTempPointer (Object val,Boolean forceLoad){
        if (temporalsMap.containsValue(val)){
            for (Map.Entry<String, Object> entry : temporalsMap.entrySet()) {
                if (entry.getValue().equals(val)) {
                    return entry.getKey();
                }
            }
        }else{
            if (forceLoad){//ensure is loaded that value
            return newTemp(val);
            }
        }
        return "";
    }
    //auxiliary methods
    //label control
    private String generateLabel() {
        return "L_" + (labelCount++);
    }

    // Method to write TAC instructions to a file
    public void writeToFile(String filePath) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            this.dataHeader.add("#-----MAIN LOOP -----");
            this.dataHeader.addAll(this.mainCalls);
            this.mainCalls = this.dataHeader;
            this.mainCalls.add("li $v0, 10"); //the program termination command
            this.mainCalls.add("syscall");
            this.mainCalls.add("#-----MAIN TERMINATION-----");
            this.mainCalls.add("#-----FUNCTIONS-----");
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

    private List<Map<String,Map<String,Object>>> search (String _search,Map<String,Map<String,Object>> to_search){
        /*
            Searches and gets the List corresponding to the Class/Instance/var etc in the given table
            @params: allo
        */
        Pattern regex = Pattern.compile(_search); // Compile the regex pattern
        List<Map<String,Map<String,Object>>> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : to_search.entrySet()) {
            String keyName = entry.getKey(); // First key
            if (regex.matcher(keyName).find()){
                results.add(new HashMap<>(){{put(entry.getKey(),entry.getValue());}});
            }
        }
        return results;
    };

    private int calculateSize (Object type){
        /*
        Calculates the size in bytes given the java type
         */
        switch (type.getClass().getSimpleName()) {
            case "Integer":
                return 4;
            case "Float":
                return 4;
            case "Double":
                return 8;
            case "Character":
                return 1;
            case "Boolean":
                return 1;
            case "String":
                return ((String) type).length() + 1;
            default:
                throw new IllegalArgumentException("Unsupported type: " + type.getClass().getSimpleName());
        }
    }

    private void addVar (String name, Map<String,Object> value,Boolean isAttribute) {
        /*
        * adds the simbols from the simbol table into .data with is corresponding mips type
        */
        if (name.split("\\.").length > 1) {
            return;
        }
        if (value.get("type") instanceof Instance) {
            //group all the attributes
            dataHeader.add(name + " :");
            List<Map<String, Map<String, Object>>> results = search(name + '.', this.ST);
            for (Map<String, Map<String, Object>> entry : results) {
                for (String key : entry.keySet()) {
                    Map<String, Object> innerMap = entry.get(key);
                    if (!(innerMap.get("type") instanceof Instance)) {
                        addVar(key.split("\\.")[1], innerMap, true);
                    }
                }
            }
            return;
        }
        Object initialValue = value.get("type");
        String javaType = value.get("type").getClass().getSimpleName();
        if (value.get("type") instanceof Param
                || value.get("type") instanceof Class
                || value.get("type") instanceof Method
                || value.get("type") instanceof Function
        ) {
            return;
        } else {
            switch (javaType) {
                case "Integer":
                    if (isAttribute) {
                        dataHeader.add(".word " + initialValue);
                    } else {
                        dataHeader.add(name + ": .word " + initialValue);
                    }
                    return;
                case "Float":
                    if (isAttribute) {
                        dataHeader.add(".float " + initialValue);
                    } else {
                        dataHeader.add(name + ": .float " + initialValue);
                    }
                    return;
                case "Double":
                    if (isAttribute) {

                    } else {
                        dataHeader.add(".double " + initialValue);
                    }
                    return;
                case "Character":
                    if (isAttribute) {
                        dataHeader.add(".byte '" + initialValue + "'");
                    } else {
                        dataHeader.add(name + ": .byte '" + initialValue + "'");
                    }
                    return;
                case "Boolean":
                    if (isAttribute) {
                        dataHeader.add(".byte " + ((Boolean) initialValue ? "1" : "0"));
                    } else {
                        dataHeader.add(name + ": .byte " + ((Boolean) initialValue ? "1" : "0"));
                    }
                    return;
                case "String":
                    if (isAttribute) {
                        dataHeader.add(".asciiz \"" + initialValue + "\"");
                    } else {
                        dataHeader.add(name + ": .asciiz \"" + initialValue + "\"");
                    }
                    return;
                default:
                    throw new IllegalArgumentException("Unsupported type: " + javaType);
            }
        }
    }

    //constructor
    public EnhancedIntermediateCode(
            HashMap<String, Map<String,Object>> fusedSymbolTable,
            HashMap<String, Map<String,Object>> fusedFunctionsTable,
            HashMap<String, Map<String,Object>> fusedClassesTable,
            HashMap<String, Map<String,Object>> fusedParametersTable
    ){
        this.ST = fusedSymbolTable;
        this.FT = fusedFunctionsTable;
        this.CT = fusedClassesTable;
        this.PT = fusedParametersTable;
        if(!ST.isEmpty()){
            for (Map.Entry<String, Map<String, Object>> entry : ST.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> value = entry.getValue();
                addVar(key, value,false);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : CT.entrySet()){
            int offset = 0;
            String name = entry.getKey();
            List<Map<String, Map<String, Object>>> results = search(name +'.',this.ST);
            for (Map<String, Map<String, Object>> attr : results) {
                for (String key : attr.keySet()) {
                    Map<String, Object> innerMap = attr.get(key);
                    if(!(innerMap.get("type") instanceof Instance)){
                        int size = calculateSize(innerMap.get("type"));
                        ST.get(key).put("size",size);
                        ST.get(key).put("offset",offset);
                        offset += size;
                    }
                }
            }
        }
    }

    // Returning the primaries:
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
        if (ctx.NUMBER() != null) {
            if (ctx.NUMBER().getText().contains(".")) {
                return Double.parseDouble(ctx.NUMBER().getText()); // Floating point number
            } else {
                return Integer.parseInt(ctx.NUMBER().getText()); // Integer
            } // Return the number
        } else if (ctx.IDENTIFIER() != null) {
            String name = ctx.IDENTIFIER().getText();
            if (ST.containsKey(name)){
                return new Variable(
                        name,
                        ST.get(name).get("type"),
                        getTempPointer(name, false)
                );
            } else if (FT.containsKey(name)) {
                return FT.get(name).get("type");
            } else if(PT.containsKey(name)){
                Param express =null;
                if(PT.get(name).containsKey("functionMapping")){

                }else{
                    return PT.get(name).get("type");
                }
            }
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());  // Parenthesized expression
        } else if (ctx.STRING() != null) {
            if(!StringConstants.containsKey(ctx.STRING().getText())){
                dataHeader.add("_S_" + constantCounter +": .asciiz " + ctx.STRING().getText());
                StringConstants.put(ctx.STRING().getText(),("_S_" + constantCounter));
                constantCounter++;
            }
            return ctx.STRING().getText();
        }else if (ctx.superCall() != null){
            return CurrFatherCall + "_" + ctx.superCall().IDENTIFIER().getText();
        } else if (ctx.getText().equals("this")) {
            return "this";
        }else if (ctx.instantiation() != null){
            return visit(ctx.instantiation());
        }
        return "";
    }

    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        // Check if there's only one factor; no need for temporary
        if (ctx.factor().size() == 1) {
            return visit(ctx.factor(0));
        }

        Object result = (visit(ctx.factor(0)));
        for (int i = 1; i < ctx.factor().size(); i++) {
            Object nextFactor = visit(ctx.factor(i));
            String op = ctx.getChild(2 * i - 1).getText();  // '+' or '-'
            String R = "", N = "";

            if (result instanceof Param){
                if(((Param) result).pointerRef.endsWith("($sp)")){
                    R = newTemp(((Param) result).getTypeInstnce());
                    instructions.add("\t".repeat(tabCounter) +"lw " + R +" , " + ((Param) result).pointerRef);

                }else {
                    R = ((Param) result).pointerRef;
                }
                result = ((Param) result).getTypeInstnce();

            }
            if (nextFactor instanceof Param){
                if(((Param) nextFactor).pointerRef.endsWith("($sp)")){
                    N = newTemp(((Param) nextFactor).getTypeInstnce());
                    instructions.add("\t".repeat(tabCounter) +"lw " + N +" , " + ((Param) nextFactor).pointerRef);

                }else {
                    N = ((Param) nextFactor).pointerRef;
                }
                nextFactor = ((Param) nextFactor).getTypeInstnce();
            }
            if (result instanceof Variable){
                if(((Variable) result).pointer.isBlank()){
                    ((Variable) result).pointer = getTempPointer(((Variable) result).name,true);
                    R = ((Variable) result).pointer;
                    instructions.add("\t".repeat(tabCounter) + "lw " + ((Variable) result).pointer +", " + ((Variable) result).name);
                    result = ((Variable) result).value;

                }
            }
            if (nextFactor instanceof Variable){
                if(((Variable) nextFactor).pointer.isBlank()){
                    ((Variable) nextFactor).pointer = getTempPointer(((Variable) nextFactor).name,true);
                    N = ((Variable) nextFactor).pointer;
                    instructions.add("\t".repeat(tabCounter) + "lw" + ((Variable) nextFactor).pointer +", " + ((Variable) nextFactor).name);
                    nextFactor = ((Variable) nextFactor).value;
                }
            }

            if (String.valueOf(result).startsWith("$t")) {
                R = String.valueOf(result);
                result = getTempVal(String.valueOf(result)) ;
                releaseTemp(R);
            }
            if (String.valueOf(nextFactor).startsWith("$t")) {
                N = String.valueOf(nextFactor);
                nextFactor =getTempVal(String.valueOf(nextFactor));
                releaseTemp(N);
            }

            // Generate a new temporary register for the result
            String temp = "";

            // Translate '+' and '-' into MIPS 'add' and 'sub' instructions
            switch (op) {
                case "+":
                    if(result instanceof Number && nextFactor instanceof Number){
                        if (result instanceof Double || nextFactor instanceof Double) {
                            temp = newTemp(((Number) result).doubleValue() + ((Number) nextFactor).doubleValue());
                        }else{
                            temp = newTemp(((Number) result).intValue() + ((Number) nextFactor).intValue());
                        }
                        result = R.isBlank()? result : R;
                        nextFactor = N.isBlank()? nextFactor : N;
                        instructions.add("\t".repeat(tabCounter) + "add " + temp + ", " + result + ", " + nextFactor);
                    } else{ //string concatenations
                        temp = newTemp(String.valueOf(result) + String.valueOf(nextFactor));
                        instructions.add("\t".repeat(tabCounter) + "concat " + temp + ", " + result + ", " + nextFactor);
                    }
                    break;
                case "-":
                    if(result instanceof Number && nextFactor instanceof Number) {
                        temp = newTemp((Double) result - (Double) nextFactor);
                        result = R.isBlank() ? result : R;
                        nextFactor = N.isBlank() ? nextFactor : N;
                        instructions.add("\t".repeat(tabCounter) + "sub " + temp + ", " + result + ", " + nextFactor);
                    }
                    break;
            }
            // Update the result to the current temporary
            result = temp;
        }

        // Release the final temporary register after use if necessary
        if (String.valueOf(result).startsWith("$t")) {
            releaseTemp(String.valueOf(result));
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

        Object result = (visit(ctx.unary(0)));
        if (result instanceof Variable){
            if(((Variable) result).pointer.isBlank()){
                ((Variable) result).pointer = getTempPointer(((Variable) result).name,true);
                instructions.add("\t".repeat(tabCounter) + "lw" + ((Variable) result).pointer +", " + ((Variable) result).name);
            }
        }

        for (int i = 1; i < ctx.unary().size(); i++) {
            Object nextUnary = (visit(ctx.unary(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '*', '/', '%'

            if (nextUnary instanceof Variable){
                if(((Variable) nextUnary).pointer.isBlank()){
                    ((Variable) nextUnary).pointer = getTempPointer(((Variable) nextUnary).name,true);
                    instructions.add("\t".repeat(tabCounter) + "lw" + ((Variable) nextUnary).pointer +", " + ((Variable) nextUnary).name);
                }
            }

            // Generate a new temporary register for the result
            String temp = newTemp(0);
            // Translate '*', '/', '%' into MIPS 'mul', 'div', and modulus instructions
            switch (op) {
                case "*":

                    instructions.add("\t".repeat(tabCounter) + "mul " + temp + ", "
                            + (result instanceof Variable? ((Variable) result).pointer :
                                result instanceof Param ? ((Param) result).pointerRef : result
                            ) + ", " +
                              (nextUnary instanceof Variable? ((Variable) nextUnary).pointer :
                                      nextUnary instanceof Param? ((Param) nextUnary).pointerRef : nextUnary));
                    break;
                case "/":
                    // MIPS division: quotient is stored in $lo, remainder in $hi
                    instructions.add("\t".repeat(tabCounter) + "div " + (result instanceof Variable? ((Variable) result).pointer :
                            result instanceof Param ? ((Param) result).pointerRef : result
                    ) + ", " +
                            (nextUnary instanceof Variable? ((Variable) nextUnary).pointer :
                                    nextUnary instanceof Param? ((Param) nextUnary).pointerRef : nextUnary));
                    instructions.add("\t".repeat(tabCounter) + "mflo " + temp); // Move quotient to temp
                    break;
                case "%":
                    // MIPS modulus: use the remainder from division, which is in $hi
                    instructions.add("\t".repeat(tabCounter) + "div " + (result instanceof Variable? ((Variable) result).pointer :
                            result instanceof Param ? ((Param) result).pointerRef : result
                    ) + ", " +
                            (nextUnary instanceof Variable? ((Variable) nextUnary).pointer :
                                    nextUnary instanceof Param? ((Param) nextUnary).pointerRef : nextUnary));
                    instructions.add("\t".repeat(tabCounter) + "mfhi " + temp); // Move remainder to temp
                    break;
            }
            if (String.valueOf(result).startsWith("$t")) {
                releaseTemp(String.valueOf(result));
            }
            // Update the result to the current temporary
            result = temp;

        }

        // Release the final temporary register after use if necessary
        if (String.valueOf(result).startsWith("$t")) {
            releaseTemp(String.valueOf(result));
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
            instructions.add("\t".repeat(tabCounter) + "li " + temp +" " + op + operand);
            releaseTemp(temp);
            return temp;
        } else {
            // Otherwise, it's a call, so just visit the call
            return visit(ctx.call());
        }
    }

    @Override
    public Object visitVarDecl(CompiScriptParser.VarDeclContext ctx) {
        // Capturamos el nombre de la variable que se está declarando
        String varName = ctx.IDENTIFIER().getText();
        currentInstanceName = varName;
        Object val = visit(ctx.expression());
        // Si es una instanciación, visitamos la expresión para capturar la instancia
        if(!(val instanceof Instance)) {
            if (ctx.expression() != null) {
                String name = ctx.IDENTIFIER().getText();
                String pointer = newTemp(val);
                instructions.add("\t".repeat(tabCounter) + "la "  + "$s1" + ", " + name);
                if(!String.valueOf(val).startsWith("$")){
                    instructions.add("\t".repeat(tabCounter) + "li" + " " + pointer + ", " + val);
                }else{
                    instructions.add("\t".repeat(tabCounter) + "move" + " " + pointer + ", " + val);
                }

                instructions.add("\t".repeat(tabCounter) + "sw "  + pointer + ", " + "0($s1)");
                releaseTemp(pointer);
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
                String pointer = newTemp(ST.get(CurrClasName + '.' + ctx.IDENTIFIER().getText()).get("type"));
                int offset = (int) ST.get(CurrClasName + '.' + ctx.IDENTIFIER().getText()).get("offset");

                //get the new expression
                Object val = visit(ctx.assignment());
                if(!String.valueOf(val).startsWith("$")){
                    instructions.add("\t".repeat(tabCounter) + "li" + " " + pointer + ", " + val);
                }else{
                    instructions.add("\t".repeat(tabCounter) + "move" + " " + pointer + ", " + val);
                }
                instructions.add("\t".repeat(tabCounter) + "sw "  + pointer + ", " + offset + "($a0)");
                releaseTemp(pointer);
            }
        }else{
            //get the new expression
            String name = ctx.IDENTIFIER().getText();
            Object val = visit(ctx.assignment());
            String pointer = newTemp(val);
            instructions.add("\t".repeat(tabCounter) + "la "  + "$s1" + ", " + name);
            instructions.add("\t".repeat(tabCounter) + "li" + " " + pointer + ", " + val);
            instructions.add("\t".repeat(tabCounter) + "sw "  + pointer + ", " + "0($s1)");
            releaseTemp(pointer);
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

        visit(ctx.expression());
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
        String blockLabel = generateLabel();
        String endLabel = generateLabel();

        // Start of the while loop
        instructions.add("\t".repeat(tabCounter) + startLabel + ":");

        // Visit condition and generate condition TAC
        LabelStack.push(blockLabel);
        inverseLabel=endLabel;
        visit(ctx.expression());
        inverseLabel="";
        LabelStack.pop();
        tabCounter ++;
        // Visit loop body
        instructions.add("\t".repeat(tabCounter) + blockLabel + ":");
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
        String blockLabel = generateLabel();
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
            LabelStack.push(blockLabel);
            inverseLabel=endLabel;
            visit(ctx.expression(0));
            inverseLabel="";
            LabelStack.pop();
        }

        // Visit loop body
        instructions.add("\t".repeat(tabCounter) + blockLabel + ":");
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
            CurrentFunction = CurrClasName + "." + functionName;
            instructions.add("\t".repeat(tabCounter) + CurrClasName.toLowerCase() + "_" + functionName.toLowerCase() + ":");
        }else{
            CurrentFunction = functionName;
            instructions.add("\t".repeat(tabCounter)  + functionName.toLowerCase() + ":");
        }

        //add the parameters
        tabCounter ++;
        int argsCounter = 0;
        if(!CurrClasName.isBlank()){
            instructions.add("\t".repeat(tabCounter) + "PARAM SELF");
            argsCounter ++;
        }

        int stackReserve = 0;
        ArrayList<String> extraParams = new ArrayList<>();

        if (ctx.parameters() != null){
            for (int i = 0; i < ctx.parameters().getChildCount(); i+=2){
                String param = String.valueOf(ctx.parameters().getChild(i).getText());
                if(PT.get(param).containsKey("functionMapping")){
                    if (argsCounter < 4){
                        ((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping")).get(CurrentFunction) ).pointerRef = "$a"+argsCounter;
                        argsCounter ++;
                    }else{
                        ((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping")).get(CurrentFunction) ).pointerRef = stackReserve +"($sp)";
                        stackReserve += calculateSize(
                                ((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping")).get(CurrentFunction)).getTypeInstnce()
                        );
                    }

                }else{
                    if (argsCounter < 4){
                        ((Param)PT.get(param).get("type")).pointerRef = "$a"+argsCounter;
                        argsCounter ++;
                    }else{
                        ((Param)PT.get(param).get("type")).pointerRef = stackReserve +"($sp)";
                        stackReserve += calculateSize(
                                ((Param)PT.get(param).get("type")).getTypeInstnce()
                        );
                    }
                }
            }
        }

        // Visit the function body (block)
        inFunction = true;
        hasReturnSmt = false;
        visit(ctx.block());
        inFunction = false;
        if (!hasReturnSmt) {
            instructions.add("\t".repeat(tabCounter) + "jr" + " " + "$ra");
        }
        hasReturnSmt = false;
        tabCounter --;
        instructions = mainCalls;
        CurrentFunction = "";
        return null;
    }

    @Override
    public Object visitReturnStmt(CompiScriptParser.ReturnStmtContext ctx){
        Object val = null;
        if (ctx.expression() != null){
            val = visit(ctx.expression());
            if(String.valueOf(val).startsWith("$")){
                instructions.add("\t".repeat(tabCounter) +"move"+" "+ "$v0" + " " + val) ;
            }else{
                instructions.add("\t".repeat(tabCounter) +"li"+" "+ "$v0" + " " + val) ;
            }
            instructions.add("\t".repeat(tabCounter) +"jr" +" " +"$ra");
        }else{
            instructions.add("\t".repeat(tabCounter) +"jr" +" " +"$ra");
        }
        hasReturnSmt = true;
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
        Object primary = (visit(ctx.primary()));
        if(primary instanceof Function){
            // Si hay argumentos en la llamada
            int argsCounter = 0;
            int stackReserve = 0;
            ArrayList<String> extraParams = new ArrayList<>();
            if(ctx.arguments() != null && !ctx.arguments().isEmpty()) {
                CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                for (int i = 0; i < arguments.getChildCount(); i += 2) {
                    Object arg = visit(arguments.getChild(i));
                    if (argsCounter < 4){
                        if(arg instanceof Number || arg instanceof String){
                            instructions.add("\t".repeat(tabCounter) + "li $a"+ argsCounter+" , "+ arg);
                        }
                        if (arg instanceof Variable){
                            if (((Variable) arg).pointer.isBlank()){
                                ((Variable) arg).pointer =
                                getTempPointer(((Variable) arg).name,true);
                            }
                            instructions.add("\t".repeat(tabCounter) + "lw $a"+ argsCounter+" , "+
                                    ((Variable) arg).pointer);
                            releaseTemp(((Variable) arg).pointer);
                        }
                        argsCounter ++;
                    }else{
                        if (arg instanceof Variable){
                            if (((Variable) arg).pointer.isBlank()){
                                ((Variable) arg).pointer =
                                        getTempPointer(((Variable) arg).name,true);
                                extraParams.add("\t".repeat(tabCounter) + "lw "+ ((Variable) arg).pointer + " , "+ ((Variable) arg).name);
                            }
                            extraParams.add("\t".repeat(tabCounter) + "sw "+ ((Variable) arg).pointer+ " , " + stackReserve+ "($sp)");
                            stackReserve += calculateSize(((Variable) arg).value);
                            releaseTemp(((Variable) arg).pointer);
                        }else{
                            String temp = newTemp(arg);
                            extraParams.add("\t".repeat(tabCounter) + "li "+ temp + " , "+ arg);
                            extraParams.add("\t".repeat(tabCounter) + "sw "+ temp + " , "+ stackReserve+ "($sp)");
                            stackReserve += calculateSize(arg);
                            releaseTemp(temp);
                        }
                    }
                }
            }
            if(extraParams.size() > 1){
                instructions.add("\t".repeat(tabCounter) +  "sub $sp, $sp, " + stackReserve);
                instructions.addAll(extraParams);
            }
            instructions.add("\t".repeat(tabCounter) + "jal "+ ((Function) primary).getFunName().toLowerCase());
            if(extraParams.size() > 1){
                instructions.add("\t".repeat(tabCounter) +  "add $sp, $sp, " + stackReserve);
            }
            return "$v0";
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
        instructions.add("\t".repeat(tabCounter) + "la $a0, " +  instanceName);
        // Manejamos los argumentos (parámetros pasados al constructor)

        if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {
            CompiScriptParser.ArgumentsContext arguments = ctx.arguments();  // Primer set de argumentos
            for (int i = 0; i < arguments.getChildCount(); i += 2) {  // Itera sobre los argumentos
                Object arg = visit(arguments.getChild(i));
                instructions.add("\t".repeat(tabCounter) + "PUSH " + arg);  // Pushea cada argumento
            }
        }

        // Llamada al constructor con la clase especificada
        instructions.add("\t".repeat(tabCounter) + "jal " + className.toLowerCase() + "_init");
        instructions.add("\t".repeat(tabCounter) + "POP " + instanceName);
        if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {

        }
        return new Instance("",className);
    }
    // Visit comparison (==, !=, >, <, >=, <=)
    @Override
    public Object visitEquality(CompiScriptParser.EqualityContext ctx) {
        Object left = visit(ctx.comparison(0));  // Visit the left side of the comparison
        Object result = left;
        String jump = "";
        if (!LabelStack.isEmpty()){
            jump = LabelStack.peek();
        }
        // If there are multiple comparisons
        for (int i = 1; i < ctx.comparison().size(); i++) {
            Object right = visit(ctx.comparison(i));
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
            if(String.valueOf(result).startsWith("$t")) {
                releaseTemp(String.valueOf(result));
            }
            result = lastInstruction;  // The result becomes the new temporary variable
        }
        if(String.valueOf(result).startsWith("$t")) {
            releaseTemp(String.valueOf(result));
        }
        return result;
    }

    @Override
    public Object visitComparison(CompiScriptParser.ComparisonContext ctx) {
        Object left = (visit(ctx.term(0)));  // Visit the left side of the comparison
        Object result = left;
        String jump = "";
        if (!LabelStack.isEmpty()){
            jump = LabelStack.peek();
        }
        // If there are multiple comparisons
        for (int i = 1; i < ctx.term().size(); i++) {
            String temp = newTemp(0);
            Object right = visit(ctx.term(i));
            String operator = ctx.getChild(2 * i - 1).getText(); //'>' | '>=' | '<' | '<='

            switch (operator){
                case ">=" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt"  + " " + temp + " " +right + " " + left);
                    if(!jump.isBlank()) {
                        instructions.add("\t".repeat(tabCounter) + "beq" + " " + temp + " $zero" + " " + jump);
                    }
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + temp + " $zero" + " " + inverseLabel);
                    }
                }
                case "<=" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp +  " " +  left + " " + right);
                    instructions.add("\t".repeat(tabCounter)  + "beq" + " " + temp + " $zero"  + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + temp + " $zero" + " " + inverseLabel);
                    }
                }
                case "<" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " + left + " " + right + " ");
                    instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + temp + " $zero" + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "beq" + " " + temp + " $zero"  + " " + inverseLabel);
                    }
                }
                case ">" -> {
                    instructions.add("\t".repeat(tabCounter)  + "slt" + " " + temp  + " " +  right + " " + left  + " ");
                    instructions.add("\t".repeat(tabCounter)  + "bne"  + " "  + temp + " $zero" + " " + jump);
                    if (!inverseLabel.isBlank()){
                        instructions.add("\t".repeat(tabCounter)  + "beq" + " " + temp + " $zero"  + " " + inverseLabel);
                    }
                }
            }
            if(String.valueOf(result).startsWith("$t")) {
                releaseTemp(String.valueOf(result));
            }

            result = temp;  // The result becomes the new temporary variable
        }
        if(String.valueOf(result).startsWith("$t")) {
            releaseTemp(String.valueOf(result));
        }
        return result;
    }

    // Visit logic OR
    @Override
    public Object visitLogic_or(CompiScriptParser.Logic_orContext ctx) {
        Object result = visit(ctx.logic_and(0));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            Object nextComparison = visit(ctx.logic_and(i + 1));
            String temp = newTemp(0);
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " || " + nextComparison);
            if(String.valueOf(result).startsWith("$t")) {
                releaseTemp(String.valueOf(result));
            }
            result = temp;
        }
        return result;
    }

    // Visit logic AND
    @Override
    public Object visitLogic_and(CompiScriptParser.Logic_andContext ctx) {
        Object result = visit(ctx.equality(0));

        for (int i = 1; i < ctx.getChildCount(); i += 2) {
            Object nextComparison = visit(ctx.equality(i + 1));
            String temp = newTemp(0);
            instructions.add("\t".repeat(tabCounter) + temp + ":= " + result + " && " + nextComparison);
            if(String.valueOf(result).startsWith("$t")) {
                releaseTemp(String.valueOf(result));
            }
            result = temp;
        }
        return result;
    }

    //IO
    @Override
    public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
        Object io = visit(ctx.expression());
        PrintValue(io,"");
        return null;
    }
    private void PrintValue(Object val, String refPoint){
        if(val instanceof Variable tmp){
            if(tmp.pointer.isBlank()){
                PrintValue(tmp.value, getTempPointer(tmp.name,true));
                return;
            }
            PrintValue(tmp.value, tmp.pointer);
        }
        int mode = -1;
        if (val instanceof Integer){
            mode = 1;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $a0 , "+ refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $a0 , "+ val);
            }
        } else if (val instanceof Double) {
            mode = 2;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $f12 , "+ refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $f12 , "+ val);
            }
        } else if (val instanceof  Float) {
            mode = 3;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $f12 , "+ refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $f12 , "+ val);
            }
        } else if( val instanceof String){
            if(String.valueOf(val).startsWith("$t")){

            }else{
                mode = 4;
                if (!refPoint.isBlank()){
                    instructions.add("\t".repeat(tabCounter) + "move $a0 , "+ refPoint);
                }else{
                    if(StringConstants.containsKey(String.valueOf(val))){ //is it a constant string?
                        instructions.add("\t".repeat(tabCounter) + "la $a0 , "
                                + StringConstants.get(String.valueOf(val)));
                    } //if not, assume is in the buffer space on .data
                    else{
                        if(!hasStringBuffer){ //no string buffer yet? create it
                            hasStringBuffer = true;
                            dataHeader.add("_B_ : .space 200");
                        }
                        instructions.add("\t".repeat(tabCounter) + "la $a0 , _B_");
                    }
                }
            }
        }
        instructions.add("\t".repeat(tabCounter) + "li $v0 , "+ mode);
        instructions.add("\t".repeat(tabCounter) + "syscall");

    }
}
