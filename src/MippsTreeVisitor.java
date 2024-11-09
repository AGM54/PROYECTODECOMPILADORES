import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;



public class MippsTreeVisitor extends CompiScriptBaseVisitor<Object> {
    //auxiliary classes of dataTypes
    private MipsInstructionsGenerator mips = new MipsInstructionsGenerator();

    /*
    flags employed
    */
    private String CurrFatherCall = ""; // for all the super.something, to get the father.something
    private String currentInstanceName = "";
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
    private HashMap<String, Map<String, Object>> ST; //symbols
    private HashMap<String, Map<String, Object>> FT; //functions
    private HashMap<String, Map<String, Object>> CT; //classes
    private HashMap<String, Map<String, Object>> PT; //parameters
    private HashMap<String, String> StringConstants = new HashMap<>(); //string constants

    // Method to write TAC instructions to a file
    public void writeToFile(String filePath) {
        mips.writeToFile(filePath);
    }

    private List<Map<String, Map<String, Object>>> search(String _search, Map<String, Map<String, Object>> to_search) {
        /*
            Searches and gets the List corresponding to the Class/Instance/var etc in the given table
            @params: allo
        */
        Pattern regex = Pattern.compile(_search); // Compile the regex pattern
        List<Map<String, Map<String, Object>>> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : to_search.entrySet()) {
            String keyName = entry.getKey(); // First key
            if (regex.matcher(keyName).find()) {
                results.add(new HashMap<>() {{
                    put(entry.getKey(), entry.getValue());
                }});
            }
        }
        return results;
    }

    ;

    private void addVar(String name, Map<String, Object> value) {
        /*
         * adds the simbols from the simbol table into .data with is corresponding mips type
         */
        if (name.split("\\.").length > 1) {
            return;
        }
        if (value.get("type") instanceof Instance) {
            //group all the attributes
            mips.saveIntoData(name);
            List<Map<String, Map<String, Object>>> results = search(name + '.', this.ST);
            for (Map<String, Map<String, Object>> entry : results) {
                for (String key : entry.keySet()) {
                    Map<String, Object> innerMap = entry.get(key);
                    if (!(innerMap.get("type") instanceof Instance)) {
                        mips.saveIntoData(key.split("\\.")[1], innerMap.get("type"), true);
                    }
                }
            }
        } else {
            ST.get(name).put("type", new Variable(name, value.get("type")));
            mips.saveIntoData(name, value.get("type"), false);
        }

    }

    //constructor
    public MippsTreeVisitor(
            HashMap<String, Map<String, Object>> fusedSymbolTable,
            HashMap<String, Map<String, Object>> fusedFunctionsTable,
            HashMap<String, Map<String, Object>> fusedClassesTable,
            HashMap<String, Map<String, Object>> fusedParametersTable
    ) {
        this.ST = fusedSymbolTable;
        this.FT = fusedFunctionsTable;
        this.CT = fusedClassesTable;
        this.PT = fusedParametersTable;
        if (!ST.isEmpty()) {
            for (Map.Entry<String, Map<String, Object>> entry : ST.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> value = entry.getValue();
                addVar(key, value);
            }
        }
        for (Map.Entry<String, Map<String, Object>> entry : CT.entrySet()) {
            int offset = 0;
            String name = entry.getKey();
            List<Map<String, Map<String, Object>>> results = search(name + '.', this.ST);
            for (Map<String, Map<String, Object>> attr : results) {
                for (String key : attr.keySet()) {
                    Map<String, Object> innerMap = attr.get(key);
                    if (!(innerMap.get("type") instanceof Instance)) {
                        int size = mips.calculateSize(innerMap.get("type"));
                        ST.get(key).put("size", size);
                        ST.get(key).put("offset", offset);
                        offset += size;
                    }
                }
            }
        }
    }
    @Override
    public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
        Object io = visit(ctx.expression());
        mips.PrintValue(io,"");
        return null;
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
            if (ST.containsKey(name)) {
                ST.get(name).get("type");
            } else if (FT.containsKey(name)) {
                return FT.get(name).get("type");
            } else if (PT.containsKey(name)) {
                if (PT.get(name).containsKey("functionMapping")) {
                    return ((HashMap<String, Param>) PT.get(name).get("functionMapping")).get(CurrentFunction);
                } else {
                    return PT.get(name).get("type");
                }
            }
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());  // Parenthesized expression
        } else if (ctx.STRING() != null) {
            mips.addStringConstant(ctx.STRING().getText());
            return ctx.STRING().getText();
        } else if (ctx.superCall() != null) {
            return CurrFatherCall + "_" + ctx.superCall().IDENTIFIER().getText();
        } else if (ctx.getText().equals("this")) {
            return new ThisDirective();
        } else if (ctx.instantiation() != null) {
            return visit(ctx.instantiation());
        } else if (ctx.superCall() != null) {
            return new SuperConstructor(ctx.superCall().getChild(2).getText());
        }
        return visitChildren(ctx);
    }

    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        // Check if there's only one factor; no need for temporary
        if (ctx.factor().size() == 1) {
            return visit(ctx.factor(0));
        }

        Object result = (visit(ctx.factor(0)));

        for (int i = 1; i < ctx.factor().size(); i++) {
            String op = ctx.getChild(2 * i - 1).getText();  // '+' or '-'
            if (result instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    result = resReg;
                } else {
                    result = mips.getRegister(var);
                }
            } else if (result instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    result = tmp;
                }
                mips.releaseRegister(r.pointer);
            } else if(result instanceof Param param){
                result = new Register(param.pointerRef,param.getTypeInstnce());
            }

            Object nextFactor = visit(ctx.factor(i));

            if (nextFactor instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    nextFactor = resReg;
                } else {
                    nextFactor = mips.getRegister(var);
                }
            } else if (nextFactor instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    nextFactor = tmp;
                    mips.moveInto(tmp.pointer, r.pointer);
                    mips.releaseRegister(tmp.pointer);
                }
                mips.releaseRegister(r.pointer);
            }else if(nextFactor instanceof Param param){
                nextFactor = new Register(param.pointerRef,param.getTypeInstnce());
            }

            // Generate a new temporary register for the result
            Register temp = null;
            Object rType = result instanceof Register ? ((Register) result).value : result;
            Object nType = nextFactor instanceof Register ? ((Register) nextFactor).value : nextFactor;
            // Translate '+' and '-' into MIPS 'add' and 'sub' instructions
            switch (op) {
                case "+":
                    if (rType instanceof Number && nType instanceof Number) {
                        if (rType instanceof Double || nType instanceof Double) {
                            temp = mips.createTemporal(((Number) rType).doubleValue() + ((Number) nType).doubleValue());
                        } else {
                            temp = mips.createTemporal(((Number) rType).intValue() + ((Number) nType).intValue());
                        }
                        mips.sum(temp, result, nextFactor);
                    } else { //string concatenations

                    }
                    break;
                case "-":
                    if (rType instanceof Double || nType instanceof Double) {
                        temp = mips.createTemporal(((Number) rType).doubleValue() + ((Number) nType).doubleValue());
                    } else {
                        temp = mips.createTemporal(((Number) rType).intValue() + ((Number) nType).intValue());
                    }
                    mips.subs(temp, result, nextFactor);
                    break;
            }
            if(result instanceof Register){
                mips.releaseRegister(((Register) result).pointer);
            }
            if(nextFactor instanceof Register){
                mips.releaseRegister(((Register) nextFactor).pointer);
            }
            result = temp;
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
        for (int i = 1; i < ctx.unary().size(); i++) {
            Object nextUnary = (visit(ctx.unary(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '*', '/', '%'
            if (result instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    result = resReg;
                } else {
                    result = mips.getRegister(var);
                }
            } else if (result instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    result = tmp;
                    mips.releaseRegister(tmp.pointer);
                }
                mips.releaseRegister(r.pointer);
            }else if(result instanceof Param param){
                result = new Register(param.pointerRef,param.getTypeInstnce());
            }

            if (nextUnary instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    nextUnary = resReg;
                } else {
                    nextUnary = mips.getRegister(var);
                }
            } else if (nextUnary instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    nextUnary = tmp;
                    mips.releaseRegister(tmp.pointer);
                }
                mips.releaseRegister(r.pointer);
            }else if(nextUnary instanceof Param param){
                nextUnary = new Register(param.pointerRef,param.getTypeInstnce());
            }


            // Generate a new temporary register for the result
            Register temp = mips.createTemporal(0);
            // Translate '*', '/', '%' into MIPS 'mul', 'div', and modulus instructions
            switch (op) {
                case "*":
                    mips.mult(temp, result, nextUnary);
                    break;
                case "/":
                    mips.div(temp, result, nextUnary);
                    break;
                case "%":
                    mips.mod(temp, result, nextUnary);
                    break;
            }
            // Update the result to the current temporary
            result = temp;

        }
        return result;
    }

    //the unary method ( still needs boolean support )
    @Override
    public Object visitUnary(CompiScriptParser.UnaryContext ctx) {
        if (ctx.getChildCount() == 2) {
            // Unary operation: either '!' or '-'
            String op = ctx.getChild(0).getText();
            Object operand = (visit(ctx.unary()));
            switch (op) {
                case "-":
                    if (operand instanceof Variable) {
                        Register curr = mips.getRegister(operand);
                        if (curr == null) {
                            curr = mips.createTemporal(operand);
                        }
                        mips.subs(curr, mips.zero, curr);
                        return curr;
                    } else if (operand instanceof Number) {
                        Register ref = null;
                        if (operand instanceof Integer) {
                            ref = mips.createTemporal(((Integer) operand) * -1);
                        } else if (operand instanceof Double) {
                            //will need to add the floating point handling tho
                            ref = mips.createTemporal(((Double) operand) * -1);
                        }
                        mips.loadInmediate(ref.pointer, operand);
                        mips.subs(ref, mips.zero, ref);
                        return ref;
                    }
                    break;
                case "!":
                    throw new RuntimeException("Oi mate, negating a boolean is not implemented yet");
            }
            // Generate TAC for the unary operation
        }
        return visit(ctx.call());
    }

    @Override
    public Object visitAssignment(CompiScriptParser.AssignmentContext ctx) {
        // Capturamos el nombre de la variable que se está declarando
        if (ctx.logic_or() != null) {
            return visit(ctx.logic_or());
        } else if (ctx.call() != null) {

        } else {
            String varName = ctx.IDENTIFIER().getText();
            currentInstanceName = varName;
            Object val = visit(ctx.assignment());
            // Si es una instanciación, visitamos la expresión para capturar la instancia
            if (!(val instanceof Instance)) {
                if (ctx.assignment() != null) {
                    String name = ctx.IDENTIFIER().getText();
                    Register saveReg = mips.createSave(null); // no value ( for now )
                    mips.loadAddres(saveReg.pointer, name);
                    if (val instanceof Register v) {
                        mips.saveWordInto(0 + saveReg.pointer, v.pointer);
                        mips.releaseRegister(v.pointer);
                    } else if (val instanceof String s) {
                        String ref = mips.getConstantLabel(s);
                    } else {
                        Register temp = mips.createTemporal(val);
                        mips.loadInmediate(temp.pointer, val);
                    }
                }
            }
        }
        currentInstanceName = "";
        return null;
    }

    /* Logic Handlig */
    @Override
    public Object visitEquality(CompiScriptParser.EqualityContext ctx) {
        if(ctx.getChildCount() == 1){
            return visit(ctx.comparison(0));
        }

        Object left = visit(ctx.comparison(0));  // Visit the left side of the comparison
        // If there are multiple comparisons
        for (int i = 1; i < ctx.comparison().size(); i++) {
            Object right = visit(ctx.comparison(i));
            String operator = ctx.getChild(2 * i - 1).getText(); // '==', '!='
            if (left instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    left = resReg;
                } else {
                    left = mips.getRegister(var);
                }
            }
            else if (left instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    left = tmp;
                }
                mips.releaseRegister(r.pointer);
            }else if(left instanceof Param param){
                left = new Register(param.pointerRef,param.getTypeInstnce());
            }
            else{
                if(left instanceof String){
                    // I dunno :v
                }else{
                    Register tmp = mips.createTemporal(left);
                    mips.loadInmediate(tmp.pointer,left);
                    left = tmp;
                }
            }

            Object nextFactor = visit(ctx.comparison(i));

            if (right instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    right = resReg;
                } else {
                    right = mips.getRegister(var);
                }
            }
            else if (right instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    right = tmp;
                }
                mips.releaseRegister(r.pointer);
            }
            else if(right instanceof Param param){
                right = new Register(param.pointerRef,param.getTypeInstnce());
            }else{
                if(right instanceof String){
                    // I dunno :v
                }else{
                    Register tmp = mips.createTemporal(right);
                    mips.loadInmediate(tmp.pointer,right);
                    right = tmp;
                }
            }
            assert left instanceof Register;
            assert right instanceof Register;
            switch (operator){
                case "==" -> {
                    mips.equals(((Register) left).pointer,((Register) right).pointer);
                }
                case "!=" -> {
                    mips.notEquals(((Register) left).pointer,((Register) right).pointer);
                }
            }
            mips.releaseRegister(((Register)left).pointer);
            mips.releaseRegister(((Register)right).pointer);
            left = right;  // The result becomes the new temporary variable
        }

       return null;
    }
    @Override
    public Object visitComparison(CompiScriptParser.ComparisonContext ctx) {
        if (ctx.getChildCount() == 1) {
            return visit(ctx.term(0));
        }

        Object left = (visit(ctx.term(0)));  // Visit the left side of the comparison
        for (int i = 1; i < ctx.term().size(); i++) {
            Object right = visit(ctx.term(i));
            String operator = ctx.getChild(2 * i - 1).getText(); // '==', '!='
            if (left instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    left = resReg;
                } else {
                    left = mips.getRegister(var);
                }
            }
            else if (left instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    left = tmp;
                }
                mips.releaseRegister(r.pointer);
            }else if(left instanceof Param param){
                left = new Register(param.pointerRef,param.getTypeInstnce());
            }else{
                if(left instanceof String){
                    // I dunno :v
                }else{
                    Register tmp = mips.createTemporal(left);
                    mips.loadInmediate(tmp.pointer,left);
                    left = tmp;
                }
            }

            Object nextFactor = visit(ctx.term(i));

            if (right instanceof Variable var) { //is a variable
                if (mips.getRegister(var) == null) {
                    //then we need to load
                    Register resReg = mips.createTemporal(var);
                    mips.loadWord(resReg.pointer, var.name);
                    right = resReg;
                } else {
                    right = mips.getRegister(var);
                }
            }
            else if (right instanceof Register r) {
                if (r.pointer.startsWith("$v")) {
                    Register tmp = mips.createTemporal(r.value);
                    mips.moveInto(tmp.pointer, r.pointer);
                    right = tmp;
                }
                mips.releaseRegister(r.pointer);
            }else if(right instanceof Param param){
                right = new Register(param.pointerRef,param.getTypeInstnce());
            }else{
                if(right instanceof String){
                    // I dunno :v
                }else{
                    Register tmp = mips.createTemporal(right);
                    mips.loadInmediate(tmp.pointer,right);
                    right = tmp;
                }
            }
            assert left instanceof Register;
            assert right instanceof Register;
            Register tmp = mips.createTemporal(((Register) right).value);
            switch (operator){
                case ">=" -> {
                    mips.lessThan(tmp.pointer,((Register) right).pointer,((Register) left).pointer);
                    mips.equals(tmp.pointer,mips.zero.pointer);
                }
                case "<=" -> {
                    mips.lessThan(tmp.pointer,((Register) left).pointer ,((Register) right).pointer);
                    mips.equals(tmp.pointer,mips.zero.pointer);
                }
                case "<" -> {
                    mips.lessThan(tmp.pointer,((Register) left).pointer ,((Register) right).pointer);
                    mips.notEquals(tmp.pointer,mips.zero.pointer);
                }
                case ">" -> {
                    mips.lessThan(tmp.pointer,((Register) right).pointer,((Register) left).pointer);
                    mips.notEquals(tmp.pointer,mips.zero.pointer);
                }
            }
            mips.releaseRegister(((Register)left).pointer);
            mips.releaseRegister(((Register)right).pointer);
            mips.releaseRegister(tmp.pointer);
            left = right;  // The result becomes the new temporary variable
        }
        return null;
    }
    // Visit logic OR
    @Override
    public Object visitLogic_or(CompiScriptParser.Logic_orContext ctx) {
        if(ctx.getChildCount() == 1){
            return visit(ctx.logic_and(0));
        }
        String label = mips.quitInverse();
        Object r = visit(ctx.logic_and(0));
        if(r instanceof Register){
            mips.releaseRegister(((Register) r).pointer);
        }

        for (int i = 1; i < ctx.logic_and().size(); i += 1) {
            if( i== ctx.logic_and().size() - 1){
                mips.addInverse(label);
            }
            Object n = visit(ctx.logic_and(i));
            if(n instanceof Register){
                mips.releaseRegister(((Register) n).pointer);
            }
        }
        return null;
    }
    // Visit logic AND
    @Override
    public Object visitLogic_and(CompiScriptParser.Logic_andContext ctx) {
        if(ctx.getChildCount() == 1){
            return visit(ctx.equality(0));
        }
        String jumpIntoNext = mips.generateLabel();
        mips.addJump(jumpIntoNext);
        Object r = visit(ctx.equality(0));
        if(r instanceof Register){
            mips.releaseRegister(((Register) r).pointer);
        }
        mips.quitJump();
        mips.pushLabel(jumpIntoNext);


        for (int i = 1; i < ctx.equality().size(); i += 1) {
            if(i+1 <ctx.equality().size()){
                jumpIntoNext = mips.generateLabel();
                mips.addJump(jumpIntoNext);
                Object n = visit(ctx.equality(i));
                if(n instanceof Register){
                    mips.releaseRegister(((Register) n).pointer);
                }
                mips.quitJump();
            }else {
                Object n = visit(ctx.equality(i));
                if(n instanceof Register){
                    mips.releaseRegister(((Register) n).pointer);
                }
            }
        }
        return null;
    }

    @Override
    public Object visitIfStmt(CompiScriptParser.IfStmtContext ctx) {
        // Generate labels for true block, false block, and end
        String labelTrue = mips.generateLabel();
        String labelElse = "";
        String labelEnd = mips.generateLabel();

        // Visit the condition expression
        mips.addJump(labelTrue);

        if (ctx.statement(1) != null) {
            labelElse = mips.generateLabel();
            inverseLabel = labelElse;
        }else{
            inverseLabel = labelEnd;
        }

        visit(ctx.expression());
        inverseLabel="";
        mips.quitJump();

        // Generate TAC for the condition
        mips.pushLabel(labelTrue);
        mips.tabsIncrease();

        // Visit the 'if' block (true case)
        visit(ctx.statement(0));  // The first statement is the 'if' body

        // Jump to end if true
        if(!mips.getInstructions().getLast().split(" ")[0].strip().stripIndent().startsWith("j")){
            mips.jumpTo(labelEnd);
        }

        mips.tabsDecrease();

        if (ctx.statement(1) != null) {
            // False block (else, if present)
            mips.pushLabel(labelElse);
            mips.addJump(labelElse);
            mips.tabsIncrease();
            visit(ctx.statement(1));  // The second statement is the 'else' body
            mips.tabsDecrease();
            mips.quitJump();
        }

        // End label
        mips.pushLabel(labelEnd);
        return null;
    }

    @Override
    public Object visitClassDecl(CompiScriptParser.ClassDeclContext ctx) {
        CurrClasName = ctx.IDENTIFIER(0).getText();
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
        mips.SwitchToLocal();
        if(!CurrClasName.isEmpty()){
            CurrentFunction = CurrClasName + "." + functionName;
            mips.pushLabel(CurrClasName.toLowerCase() + "_" + functionName.toLowerCase());
        }else{
            CurrentFunction = functionName;
            mips.pushLabel(functionName.toLowerCase());
        }

        //add the parameters
        mips.tabsIncrease();
        int argsCounter = 0;
        if(!CurrClasName.isBlank()){
            argsCounter ++;
        }

        int stackReserve = 0;
        ArrayList<String> extraParams = new ArrayList<>();

        if (ctx.parameters() != null){
            for (int i = 0; i < ctx.parameters().getChildCount(); i+=2){
                String param = String.valueOf(ctx.parameters().getChild(i).getText());
                if(PT.get(param).containsKey("functionMapping")){
                    ((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping")).get(CurrentFunction) ).pointerRef
                            = mips.createArgument(((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping"))
                            .get(CurrentFunction)).getTypeInstnce()).pointer;
                    if (argsCounter > 3){
                        stackReserve += mips.calculateSize(
                                ((Param) ((HashMap<String,Param>) PT.get(param).get("functionMapping")).get(CurrentFunction)).getTypeInstnce()
                        );
                    }
                    argsCounter ++;
                }else{
                        ((Param)PT.get(param).get("type")).pointerRef
                                = mips.createArgument(((Param)PT.get(param).get("type")).getTypeInstnce()).pointer;
                        if (argsCounter > 3) {
                            stackReserve += mips.calculateSize( ((Param)PT.get(param).get("type")).getTypeInstnce());
                        }
                        argsCounter ++;
                }
            }
        }

        // Visit the function body (block)
        inFunction = true;
        hasReturnSmt = false;
        visit(ctx.block());
        mips.resetArguments();
        inFunction = false;
        if (!hasReturnSmt) {
            mips.jumpReturn();
        }
        hasReturnSmt = false;
        mips.tabsDecrease();
        mips.SwitchToMain();

        CurrentFunction = "";
        return null;
    }

    @Override
    public Object visitReturnStmt(CompiScriptParser.ReturnStmtContext ctx){
        Register returnPointer = null;
        if (ctx.expression() != null){
            Object val = visit(ctx.expression());
            if(val instanceof Register reg){
                returnPointer = mips.setReturnRegister(reg.value);
                mips.moveInto(returnPointer.pointer, reg.pointer);
            }else if(val instanceof Param param){
                returnPointer = mips.setReturnRegister(param.getTypeInstnce());
                mips.moveInto(returnPointer.pointer, param.pointerRef);
            }else if(val instanceof Variable var) {
                returnPointer = mips.setReturnRegister(var.value);
                Register pointer = mips.getRegister(var);
                if(pointer == null){
                    pointer = mips.createTemporal(var);
                    mips.loadWord(pointer.pointer,var.name);
                }
                mips.moveInto(returnPointer.pointer, pointer.pointer);
            }
            else{
                if(val instanceof String){
                    returnPointer = mips.setReturnRegister(val);
                }else{
                    returnPointer = mips.setReturnRegister(val);
                    mips.loadInmediate(returnPointer.pointer, val);
                }
            }
        }
        mips.jumpReturn();
        hasReturnSmt = true;
        return returnPointer;
    }


}
