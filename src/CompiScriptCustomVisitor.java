import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import javax.lang.model.type.DeclaredType;
import java.util.regex.Pattern;
import java.util.*;

class Function {
    private  String funName;
    private Boolean isRecursive = false;
    private Object returnsType = null;
    public Function(String funName){
        this.funName = funName;
    }
    public  String getFunName(){
        return this.funName;
    }
    private CompiScriptParser.FunctionContext ctx = null;

    public void setCtx(CompiScriptParser.FunctionContext ctx) {
        this.ctx = ctx;
    }

    public CompiScriptParser.FunctionContext getCtx() {
        return ctx;
    }

    public void setIsRecursive(){
        isRecursive = true;
    }
    public Boolean getIsRecursive(){
        return isRecursive;
    }
    public void setReturnsType(Object returnsType){
        this.returnsType = returnsType;
    }
    public Object getReturnsType(){
        return this.returnsType;
    }
} //a function
class Class {} // a class , no rocket science
class Instance{
    private String clasName;
    private String lookUpName;
    Instance(String classname,String lookUpName){this.clasName = classname;this.lookUpName = lookUpName;}
    public String getClasName() {
        return clasName;
    }
    public String getLookUpName(){
        return  lookUpName;
    }
}
class Undefined {} //variables that only have been declared, but no value assigned
class Method extends Function{
    public Method(String functionName) {
        super(functionName);
    }
}  //for methods , functions inside a class
class Unknown {} //for all symbols that were not found
class ThisDirective{}
class SuperConstructor {
    private String Identifier;
    public SuperConstructor(String Identifier){
        this.Identifier = Identifier;
    }
    public String getIdentifier(){
        return this.Identifier;
    }
}
class Param{
    private Object typeInstnce = null;

    public void setTypeInstnce(Object typeInstnce) {
        this.typeInstnce = typeInstnce;
    }
    public Object getTypeInstnce() {return typeInstnce;}
} //for the parameters, will be assumed as correct when function is declared
public class CompiScriptCustomVisitor   extends CompiScriptBaseVisitor<Object> {
    //Stack para los contextos
    private Stack<String> ScopesStack = new Stack<>(){{push("0");}};

    //the symbol table WILL BE CREATED FOR EACH SCOPE: Scope: SYMBOL TABLE
    private Map<String, Map<String,Map<String,Object>>>
            scopedSymbolTable = new HashMap<>(){{put("0",new HashMap<>());}};
    //symbol table just for the classes
    private Map<String, Map<String,Map<String,Object>>>
            scopedDeclaredClasses = new HashMap<>(){{put("0",new HashMap<>());}};
    // Tabla de símbolos para almacenar funciones
    private Map<String, Map<String,Map<String,Object>>>
            scopedDeclaredFunctions = new HashMap<>(){{put("0",new HashMap<>());}};
    private Map<String, Map<String,Map<String,Object>>>
            scopedParametersDeclarations = new HashMap<>(){{put("0",new HashMap<>());}};
    //Flag para el manejo de declaracion de clases , funciones y variables de tipo Instancia o experesiones
    private String CurrClasName = "" ;
    private String CurrVarDefining = "" ;
    private String CurrFuncName = "";
    private Integer AnonCount = 0; //for all blocks that are on Anon fucntion
    private Boolean VisitingSuper = false; // to visit and retrieve methods and attributes from super class to avoid semantic exceptions of already defined
    private String currCallName = "" ; // call made
    private String currInstanceOf = "";
    private String instanceCall = "";
    // Tabla de símbolos para almacenar variables globales

    // get a list of all the methods of a class  ( useful for heriachy and inherance )
    public List<Map<String,Map<String,Object>>> findFromOnTable(String regrexSearch , Map<String, Map<String,Map<String,Object>>> HashTble,String Scope){
        Pattern regex = Pattern.compile(regrexSearch); // Compile the regex pattern
        List<Map<String,Map<String,Object>>> results = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> entry : HashTble.get(Scope).entrySet()) {
            String keyName = entry.getKey(); // First key
            if (regex.matcher(keyName).find()){
                results.add(new HashMap<>(){{put(entry.getKey(),entry.getValue());}});
            }
        }
        return results;
    }


    public void printSymbols() {
        System.out.println("Symbols: ");
        System.out.println("|Name|\t|Scope|\t|Type|\t|Super|\t|Arguments|");
        System.out.println("Declared Symbols : \n");
        scopedSymbolTable.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))){
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + (data.getOrDefault("type","") == null ? "null" : data.getOrDefault("type","").getClass().getSimpleName() )  + "|" + data.getOrDefault("father", "") + "|" +
                            data.getOrDefault("params", "")
                    );
                }

            });
        });
        System.out.println("\nDeclared Classes : \n");
        scopedDeclaredClasses.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))) {
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + (data.getOrDefault("type", "") == null ? "null" : data.getOrDefault("type", "").getClass().getSimpleName())
                            + "|" + data.getOrDefault("father", "") + "|" +
                            data.getOrDefault("params", "")
                    );
                }
            });
        });
        System.out.println("\nDeclared functions :\n");
        scopedDeclaredFunctions.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))){
                System.out.println("|" + id + "|" + data.get("scope") + "|"
                        + (data.getOrDefault("type","") == null ? "null" : data.getOrDefault("type","").getClass().getSimpleName() ) + "|" + data.getOrDefault("father","") + "|" +
                        data.getOrDefault("params","")
                );
                }
            });
        });

        System.out.println("\nDeclared parameters :\n");
        scopedParametersDeclarations.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))) {
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + (((Param) data.get("type")).getTypeInstnce() == null ? data.get("type").getClass().getSimpleName() : ((Param) data.get("type")).getTypeInstnce().getClass().getSimpleName())
                            + "|" + data.getOrDefault("father", "") + "|" +
                            data.getOrDefault("params", ""));
                }
            });
        });
    }
    /*
    Sección de manejo de valores atómicos o primarios
     */
    // Helper method to convert any object to a String
    private String convertToString(Object obj) {
        if (obj instanceof Character) {
            return String.valueOf((Character) obj);
        } else if (obj instanceof Number) {
            return String.valueOf((Number) obj);
        } else if (obj instanceof Boolean) {
            return String.valueOf((Boolean) obj);
        }  else if (obj instanceof String) {
            // Fallback for any other type including String
            return String.valueOf(obj);
        }else if (obj instanceof String) {
            return  "";
        }
        else{
            throw new RuntimeException("Error : cannot cast into string a non primitive value, received " + obj.getClass().getSimpleName());
        }
    }
    //manejo de tipos primarios
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
        if (ctx.NUMBER() != null) {
            if (ctx.NUMBER().getText().contains(".")) {
                return Double.parseDouble(ctx.NUMBER().getText()); // Floating point number
            } else {
                return Integer.parseInt(ctx.NUMBER().getText()); // Integer
            }
        } else if (ctx.STRING() != null) {
            // Remove the surrounding quotes
            return ctx.STRING().getText().substring(1, ctx.STRING().getText().length() - 1);
        } else if (ctx.IDENTIFIER() != null) {
            String varName = ctx.IDENTIFIER().getText();
            if (scopedSymbolTable.get(ScopesStack.peek()).containsKey(varName)) {
                return scopedSymbolTable.get(ScopesStack.peek()).get(varName).get("type");
            }
            else if (scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(varName)) {
                return scopedDeclaredClasses.get(ScopesStack.peek()).get(varName).get("type");
            }
            else if (scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(varName)) {
                return scopedDeclaredFunctions.get(ScopesStack.peek()).get(varName).get("type");
            }else if(scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(varName)) {
                Param express = (Param)scopedParametersDeclarations.get(ScopesStack.peek()).get(varName).get("type");
                if(express.getTypeInstnce() != null){
                    return express.getTypeInstnce();
                }else{
                    return express;
                }
            }
            else {
                throw new RuntimeException("Error: Undeclared symbol :" + varName);
            }
        } else if (ctx.getText().equals("true")) {
            return true;
        } else if (ctx.getText().equals("false")) {
            return false;
        } else if (ctx.getText().equals("nil")) {
            return null;
        } else if (ctx.expression() != null) {
            return visit(ctx.expression()); // Parenthesized expression
        } else if (ctx.array() != null) {
            return visit(ctx.array());
        } else if (ctx.instantiation() != null) {
            return visit(ctx.instantiation());
        } else if(ctx.getText().equals("this")){
            return new ThisDirective();
        } else if (ctx.superCall() != null){
            return new SuperConstructor(ctx.superCall().getChild(2).getText());
        }
        return visitChildren(ctx);
    }

    //Manejo de asignaciones unarias:
    @Override
    public Object visitUnary(CompiScriptParser.UnaryContext ctx) {
        // The unary operator is the first child if it exists, followed by the operand.
        if (ctx.getChildCount() == 2) {
            Object value = visit(ctx.unary());
            String operator = ctx.getChild(0).getText(); // Access the operator directly
            if ("-".equals(operator)) {
                if (value instanceof Number) {
                    if (value instanceof Double) {
                        return -((Double) value);
                    } else {
                        return -((Integer) value);
                    }
                } else {
                    throw new RuntimeException("Unary '-' operator can only be applied to numbers.");

                }
            } else if ("!".equals(operator)) {
                if (value instanceof Boolean) {
                    return !((Boolean) value);
                } else {
                    throw new RuntimeException("Unary '!' operator can only be applied to boolean values.");

                }
            }
        }

        // If it's not a unary operation, just visit the call (the next production rule)
        return visit(ctx.call());
    }
    // Maneja valores primarios (números, etc.)

    /* Manejo de operaciones elementales */
    // Visita una expresión de suma o resta (term)
    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        Object result = visit(ctx.factor(0));

        for (int i = 1; i < ctx.factor().size(); i++) {

            Object nextValue = visit(ctx.factor(i));
            //if somehow both are params using default Int
            if (result instanceof Param && nextValue instanceof Param) {
                result = 0;
                nextValue = 0;
            }
            if (nextValue instanceof Param) {
                Object innerType = (Param) ((Param) nextValue).getTypeInstnce();
                if(innerType == null) {
                    nextValue = result;
                }
                else{
                    nextValue = innerType;
                }
            }
            String operator = ctx.getChild(2 * i - 1).getText();
            if (result instanceof Param) {
                Object innerType = (Param) ((Param) result).getTypeInstnce();
                if(innerType == null) {
                    result = nextValue;
                }
                else{
                    result = innerType;
                }
            }

            if (operator.equals("+")) {
                if (result instanceof Number && nextValue instanceof Number) {
                    if (result instanceof Double || nextValue instanceof Double) { // any with double = a double no matter is its int + double
                        result = ((Number) result).doubleValue() + ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() + ((Number) nextValue).intValue();
                    }
                } else if (result instanceof String || nextValue instanceof String) {
                    // Safely convert to String, handling null values
                    String resultString = (result == null) ? "null" : convertToString(result);
                    String nextValueString = (nextValue == null) ? "null" : convertToString(nextValue);

                    // Perform String concatenation
                    result = resultString + nextValueString;

                } else {
                    throw new RuntimeException("Operands must be both numbers or both strings for '+' operation. , found: "+ result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
                }
            } else if (operator.equals("-")) {
                if (result instanceof Number && nextValue instanceof Number ) {
                    if (result instanceof Double || nextValue instanceof Double) {
                        result = ((Number) result).doubleValue() - ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() - ((Number) nextValue).intValue();
                    }
                } else {
                    throw new RuntimeException("Operands must be numbers for substraction '-' operation. " + result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
                }
            }
        }
        return result instanceof  Param ? 0 : result; //siempre se regresa el unario de no ser que no hayan más , caso opuesto dictamina el return type en estas operaciones
    }

    //modulo, multiplicacion, division (factor)
    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        Object result = visit(ctx.unary(0));

        for (int i = 1; i < ctx.unary().size(); i++) {
            Object nextValue = visit(ctx.unary(i));
            //if somehow both are params using default Int
            if (result instanceof Param && nextValue instanceof Param) {
                result = 0;
                nextValue = 0;
            }
            if (nextValue instanceof Param) {
                Object innerType = (Param) ((Param) nextValue).getTypeInstnce();
                if(innerType == null) {
                    nextValue = result;
                }
                else{
                    nextValue = innerType;
                }
            }
            String operator = ctx.getChild(2 * i - 1).getText();
            if (result instanceof Param) {
                Object innerType = (Param) ((Param) result).getTypeInstnce();
                if(innerType == null) {
                    result = nextValue;
                }
                else{
                    result = innerType;
                }
            }
            if (result instanceof Number && nextValue instanceof Number) {
                switch (operator) {
                    case "*":
                        if (result instanceof Double || nextValue instanceof Double) {
                            result = ((Number) result).doubleValue() * ((Number) nextValue).doubleValue();
                        } else {
                            result = ((Number) result).intValue() * ((Number) nextValue).intValue();
                        }
                        break;
                    case "/":
                        if (result instanceof Double || nextValue instanceof Double) {
                            result = ((Number) result).doubleValue() / ((Number) nextValue).doubleValue();
                        } else {
                            result = ((Number) result).intValue() / ((Number) nextValue).intValue();
                        }
                        break;
                    case "%":
                        if (result instanceof Double || nextValue instanceof Double) {
                            result = ((Number) result).doubleValue() % ((Number) nextValue).doubleValue();
                        } else {
                            result = ((Number) result).intValue() % ((Number) nextValue).intValue();
                        }
                        break;
                    default:
                        throw new RuntimeException("Unknown operator: " + operator);
                }
            } else {
                throw new RuntimeException("Operands must be numbers for '*' '/' '%' operations. found: " + result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
            }
        }

        return result instanceof  Param ? 0 : result; //siempre se regresa el unario de no ser que no hayan más , caso opuesto dictamina el return type en estas operaciones
    }

    /*Manejo de Variables*/

    // declaracion de variables
    @Override
    public Object visitVarDecl(CompiScriptParser.VarDeclContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        if (varName == null || varName.isBlank() || varName.isEmpty()) {
            throw new RuntimeException("Error: Var must have an identifier");
        }
        //check if is not defined or if is not defined on this scope
        if (!scopedSymbolTable.get(ScopesStack.peek().toString()).containsKey(varName)) {
            HashMap<String,Object> varMap = new HashMap<>();
            CurrVarDefining = varName;
            if (ctx.expression() != null) {
                Object type = visit(ctx.expression());
                varMap.put("type",type);
            }else{ //is just defined, no assigmen
                varMap.put("type",new Undefined()); // the new Undefined() if there was no type retrieved
            }
            CurrVarDefining = "";
            varMap.put("scope",ScopesStack.peek());
            scopedSymbolTable.get(ScopesStack.peek().toString()).put(varName, varMap);
        }else{
            throw new RuntimeException("Error: Variable already defined " + varName);
        }
        return null;
    }

    /*Manejo de Funciones*/

    // Visita la declaración de una función
    @Override
    public Object visitFunction(CompiScriptParser.FunctionContext ctx) {
        // Obtén el nombre de la función del primer identificador en la lista
        String functionName = currInstanceOf.isBlank()? ctx.IDENTIFIER().getText() : currInstanceOf + '.' + ctx.IDENTIFIER().getText() ;
        HashMap<String,Object> functMap = new HashMap<>();
        functMap.put("params",new ArrayList<>());
        String originalScope = ScopesStack.peek();
        if(ctx.parameters() != null) {
            List<String> parameters = new ArrayList<>();
            for (TerminalNode param : ctx.parameters().IDENTIFIER()) {
                parameters.add(param.getText());
            }
            functMap.put("params",parameters);
            //push the parameters into the scope

        }

        CurrFuncName = functionName ;
        functMap.put("scope", ScopesStack.peek());
        functMap.put("returns",null);

        functMap.put("ctx",ctx);
        if (CurrClasName.isEmpty()) {
            // Guarda la función en la tabla de funciones
            functMap.put("type", new Function(functionName) {{
                setCtx(ctx);
            }});
            if (!scopedDeclaredFunctions.get(ScopesStack.peek().toString()).containsKey(functionName)) {
                scopedDeclaredFunctions.get(ScopesStack.peek().toString()).put(functionName, functMap);
            } else {
                if (!VisitingSuper) {
                    throw new RuntimeException("Error: Function already defined " + functionName);
                }
            }
        } else {
            // Guardar en la clase
            CurrFuncName = CurrClasName + "." + functionName;
            if (scopedDeclaredClasses.get(ScopesStack.peek().toString()).containsKey(CurrClasName)) {
                if (!scopedDeclaredFunctions.get(ScopesStack.peek().toString()).containsKey(CurrClasName + "." + functionName)) {

                    functMap.put("type", new Method(functionName) {{
                        setCtx(ctx);
                    }});
                    scopedDeclaredFunctions.get(ScopesStack.peek().toString()).put(CurrClasName + "." + functionName, functMap);
                    ;
                } else {
                    if (!VisitingSuper) {
                        throw new RuntimeException("Error: Method already defined: " + CurrClasName + "." + functionName);
                    }
                }
            } else {
                // Handle the case where CurrClasName is null or does not exist in declaredClasses
                throw new RuntimeException("Error: " + CurrClasName + " not found in declaredClasses.");
            }
        }

        // Populate new symbol tables from the existing scope
        Map<String, Map<String, Object>> functsMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        // Insert new scope into the symbol table maps
        String newScope = Integer.toString((functMap.size() + symbolMap.size()+classMap.size()+paramMap.size()));
        scopedDeclaredFunctions.put(newScope, functsMap);
        scopedSymbolTable.put(newScope, symbolMap);
        scopedDeclaredClasses.put(newScope, classMap);
        scopedParametersDeclarations.put(newScope, paramMap);

        // Push the new Scope into the stack
        ScopesStack.push(newScope);
        //push the parameters into the scope
        for (String param : (List<String>) functMap.get("params")) {
            if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(param)) {
                scopedParametersDeclarations.get(ScopesStack.peek()).put(param,
                        new HashMap<>() {{
                            put("type", new Param());
                            put("scope", ScopesStack.peek());
                        }});
            } else {
                Param paramI = (Param) scopedParametersDeclarations.get(ScopesStack.peek()).get(param).get("type");
                if (paramI.getTypeInstnce() == null) {
                    if(!VisitingSuper) {
                        throw new RuntimeException("Error : Parameter " + param + " is already declared ");
                    }
                }
            }
        }
        Object returner = visitChildren(ctx);
        if (returner!=null) {
            if (CurrClasName.isEmpty()) {
                ((Function) scopedDeclaredFunctions.get(originalScope).get(CurrFuncName).get("type")).setReturnsType(returner);
            } else {
                ((Method) scopedDeclaredFunctions.get(originalScope).get(CurrFuncName).get("type")).setReturnsType(returner);
            }
        }
        CurrFuncName = "";
        // Pop the Scope after processing
        String lastScope = ScopesStack.pop();

        //if its inside a var declaration or a class declaration recover all the declared stuff and bring back
        if(!CurrClasName.isBlank()) {
            List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrClasName + '.', scopedSymbolTable, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lastDeclaredFunctions = findFromOnTable(CurrClasName + '.', scopedDeclaredFunctions, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredFun : lastDeclaredFunctions) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredFun.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedDeclaredFunctions.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lasDeclaredParams = findFromOnTable(CurrClasName + '.', scopedParametersDeclarations, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lasDeclaredParams) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedParametersDeclarations.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lastDeclaredClasses = findFromOnTable(CurrClasName + '.', scopedDeclaredClasses, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredClasses) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedDeclaredClasses.get(ScopesStack.peek()).put(key, value);
                }
            }
        }if(VisitingSuper && !CurrVarDefining.isBlank() && !currInstanceOf.isEmpty()){
            List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrVarDefining + '.', scopedSymbolTable, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                }
            }
        }
        return null;
    }

    //anonimous function block
    @Override
    public Object visitFunAnon(CompiScriptParser.FunAnonContext ctx){
        CurrFuncName="Anon"+AnonCount;
        HashMap<String,Object> functMap = new HashMap<>();
        if(ctx.parameters() != null) {
            List<String> parameters = new ArrayList<>();
            for (TerminalNode param : ctx.parameters().IDENTIFIER()) {
                parameters.add(param.getText());
            }
            functMap.put("params",parameters);
        }
        functMap.put("scope", ScopesStack.peek());
        functMap.put("returns",null);
        functMap.put("ctx",ctx);
        if (CurrClasName.isEmpty()){
            // Guarda la función en la tabla de funciones
            functMap.put("type",new Function(CurrFuncName));
            if(!scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(CurrFuncName)) {
                scopedDeclaredFunctions.get(ScopesStack.peek()).put(CurrFuncName, functMap);
                visitChildren(ctx);
                CurrFuncName = "";
            }else{
                throw new RuntimeException("Error: Function already defined" + CurrFuncName);
            }
        }else{
            // Guardar en la clase
            if (scopedDeclaredClasses.get(ScopesStack.peek().toString()).containsKey(CurrClasName)) {
                if(!scopedDeclaredFunctions.get(ScopesStack.peek().toString()).containsKey(CurrClasName + "." + CurrFuncName)){
                    functMap.put("type",new Method(CurrFuncName){{}});
                    scopedDeclaredFunctions.get(ScopesStack.peek().toString()).put(CurrClasName + "." + CurrFuncName,functMap);;
                    visitChildren(ctx);
                    CurrFuncName = "";
                }else{
                    throw new RuntimeException("Error: Method already defined" + CurrClasName + "." + CurrFuncName);
                }

            } else {
                // Handle the case where CurrClasName is null or does not exist in declaredClasses
                throw new RuntimeException("Error: " + CurrClasName + " not found in declaredClasses.");
            }
        }
        AnonCount++;
        return null;
    }

    //the return statement
    @Override
    public Object visitReturnStmt(CompiScriptParser.ReturnStmtContext ctx) {
        if (ctx.expression()!= null) {
            return visit(ctx.expression());
        }
        // a simple return , return a null
        return null;
    }

    //Manejo de llamadas
    @Override
    public Object visitCall(CompiScriptParser.CallContext ctx) {
        Object returner = null;
        if (ctx.getChildCount() == 1){ //primary call
            if (ctx.primary() != null){
                if(ctx.primary().array() != null) { // is an array (somehow)
                    return visit(ctx.primary().array());
                }else { //just a primary
                    return visit(ctx.primary());
                }
            }

        }else if(ctx.primary() != null){
            Object primary = visit(ctx.primary());
            if(primary instanceof  Function){
                if(!(CurrFuncName.equals(((Function) primary).getFunName()) || currCallName.equals(((Function) primary).getFunName()))){
                    currCallName = ((Function) primary).getFunName();
                    List<String> requParams = (List<String>) scopedDeclaredFunctions.get(ScopesStack.peek()).get(((Function) primary).getFunName()).get("params");
                    List<Object> receivedParams = new ArrayList<>();
                    if(ctx.arguments() != null && !ctx.arguments().isEmpty()){
                        CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                        for(int i= 0; i <arguments.getChildCount() ; i+=2){
                            receivedParams.add(visit(arguments.getChild(i)));
                        }
                    }

                    if(requParams!=null && requParams.size() != receivedParams.size()){
                        throw new RuntimeException("Error: " +((Function) primary).getFunName() + " requieres " +(requParams==null ? 0 : requParams.size())
                                + " parameters " + receivedParams.size() + " found");
                    }
                    if(requParams != null ) { //avoid calling recursively because a loop
                        // Create new symbol tables for the new scope
                        // Populate new symbol tables from the existing scope
                        Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                        Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                        Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                        Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                        // Insert new scope into the symbol table maps
                        String newScope = Integer.toString((functMap.size() + symbolMap.size() + classMap.size() + paramMap.size()));
                        scopedDeclaredFunctions.put(newScope, functMap);
                        scopedSymbolTable.put(newScope, symbolMap);
                        scopedDeclaredClasses.put(newScope, classMap);
                        scopedParametersDeclarations.put(newScope, paramMap);
                        // Push the new Scope into the stack
                        ScopesStack.push(newScope);
                        //push the parameters into the scope
                        for (int i = 0; i < requParams.size(); i++) {
                            String paramName = requParams.get(i).toString();
                            Object type = receivedParams.get(i);
                            if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(paramName)) {
                                scopedParametersDeclarations.get(ScopesStack.peek()).put(paramName,
                                        new HashMap<>() {{
                                            put("type", new Param() {{
                                                setTypeInstnce(type);
                                            }});
                                            put("scope", ScopesStack.peek());
                                        }});
                            } else {
                                Param paramI = (Param) scopedParametersDeclarations.get(ScopesStack.peek()).get(paramName).get("type");
                                if (paramI.getTypeInstnce() == null && currCallName.isEmpty()) {
                                    throw new RuntimeException("Error : Parameter " + paramName + " is already declared ");
                                }
                            }
                        }
                        returner = visitChildren(((Function) primary).getCtx());
                        ScopesStack.pop();
                        currCallName = "";
                    }
                } if (CurrFuncName.equals(((Function) primary).getFunName()) || (currCallName.equals(((Function) primary).getFunName()))){
                    if(!CurrFuncName.isEmpty()){
                        //add the recursive flag
                        ( ( Function)scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrFuncName).get("type") ).setIsRecursive();
                    }
                    return new Param();
                }
            }
            if (primary instanceof  ThisDirective) {
                if (CurrClasName.isBlank()) {
                    throw new RuntimeException("No class found for usage of 'this' call");
                } else if (!instanceCall.isBlank()) {
                    String attr = instanceCall + '.' + ctx.IDENTIFIER().getFirst().getText();
                    return scopedSymbolTable.get(ScopesStack.peek()).get(attr).get("type");
                } else {
                    if (ctx.IDENTIFIER() != null) {
                        if (scopedSymbolTable.get(ScopesStack.peek()).containsKey(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())) {
                            returner = scopedSymbolTable.get(ScopesStack.peek()).get(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()).get("type");
                        }
                        else if (scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())) {
                            if(!( CurrFuncName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())|| currCallName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()))) {
                                //just check if the parameters are well received and no errors when calling it
                                Method currMethod = (Method) scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()).get("type");
                                currCallName = CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText();
                                List<String> requParams = (List<String>) scopedDeclaredFunctions.get(ScopesStack.peek()).get(currCallName).get("params");
                                List<Object> receivedParams = new ArrayList<>();
                                if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {
                                    CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                                    for (int i = 0; i < arguments.getChildCount(); i += 2) {
                                        receivedParams.add(visit(arguments.getChild(i)));
                                    }
                                }

                                if (requParams != null && requParams.size() != receivedParams.size()) {
                                    throw new RuntimeException("Error: " + currCallName + " requieres " + (requParams == null ? 0 : requParams.size())
                                            + " parameters " + receivedParams.size() + " found");
                                }
                                if (requParams != null) { //avoid calling recursively because a loop
                                    // Create new symbol tables for the new scope
                                    // Populate new symbol tables from the existing scope
                                    Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                    Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                    Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                    Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                    // Insert new scope into the symbol table maps
                                    String newScope = Integer.toString((functMap.size() + symbolMap.size() + classMap.size() + paramMap.size()));
                                    scopedDeclaredFunctions.put(newScope, functMap);
                                    scopedSymbolTable.put(newScope, symbolMap);
                                    scopedDeclaredClasses.put(newScope, classMap);
                                    scopedParametersDeclarations.put(newScope, paramMap);
                                    // Push the new Scope into the stack
                                    ScopesStack.push(newScope);
                                    //push the parameters into the scope
                                    for (int i = 0; i < requParams.size(); i++) {
                                        String paramName = requParams.get(i).toString();
                                        Object type = receivedParams.get(i);
                                        if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(paramName)) {
                                            scopedParametersDeclarations.get(ScopesStack.peek()).put(paramName,
                                                    new HashMap<>() {{
                                                        put("type", new Param() {{
                                                            setTypeInstnce(type);
                                                        }});
                                                        put("scope", ScopesStack.peek());
                                                    }});
                                        } else {
                                            Param paramI = (Param) scopedParametersDeclarations.get(ScopesStack.peek()).get(paramName).get("type");
                                            if (paramI.getTypeInstnce() == null && currCallName.isEmpty()) {
                                                throw new RuntimeException("Error : Parameter " + paramName + " is already declared ");
                                            }
                                        }
                                    }
                                }
                                returner = visitChildren(currMethod.getCtx());
                                ScopesStack.pop();
                                currCallName = "";
                            }
                            else if ( CurrFuncName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()) || currCallName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())){
                                if(!CurrFuncName.isEmpty()){
                                    //add the recursive flag
                                    ( ( Method)scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrFuncName).get("type") ).setIsRecursive();
                                }
                                return new Param();
                            }
                        }
                        else {
                                //check on the old scope if it exists
                                String actual = ScopesStack.pop(); //remove the actual
                                if (scopedSymbolTable.get(ScopesStack.peek()).containsKey(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())) {
                                    returner = scopedSymbolTable.get(ScopesStack.peek()).get(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()).get("type");
                                    //if it does, push it also into the actual
                                    Map<String, Object> att = scopedSymbolTable.get(ScopesStack.peek()).get(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText());
                                    ScopesStack.push(actual);
                                    scopedSymbolTable.get(ScopesStack.peek()).put(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText(), att);
                                } else if(scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())) {
                                    //just check if the parameters are well received and no errors when calling it
                                    if(!( CurrFuncName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()) || currCallName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()))) {
                                        if(!(currCallName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()))) {
                                        Method currMethod = (Method) scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()).get("type");
                                        currCallName = CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText();
                                        List<String> requParams = (List<String>) scopedDeclaredFunctions.get(ScopesStack.peek()).get(currCallName).get("params");
                                        List<Object> receivedParams = new ArrayList<>();
                                        if (ctx.arguments() != null && !ctx.arguments().isEmpty()) {
                                            CompiScriptParser.ArgumentsContext arguments = ctx.arguments().getFirst();
                                            for (int i = 0; i < arguments.getChildCount(); i += 2) {
                                                receivedParams.add(visit(arguments.getChild(i)));
                                            }
                                        }

                                        if (requParams != null && requParams.size() != receivedParams.size()) {
                                            throw new RuntimeException("Error: " + currCallName + " requieres " + (requParams == null ? 0 : requParams.size())
                                                    + " parameters " + receivedParams.size() + " found");
                                        }
                                        if (requParams != null) { //avoid calling recursively because a loop
                                            // Create new symbol tables for the new scope
                                            // Populate new symbol tables from the existing scope
                                            Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                            Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                            Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                            Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                            // Insert new scope into the symbol table maps
                                            String newScope = Integer.toString((functMap.size() + symbolMap.size() + classMap.size() + paramMap.size()));
                                            scopedDeclaredFunctions.put(newScope, functMap);
                                            scopedSymbolTable.put(newScope, symbolMap);
                                            scopedDeclaredClasses.put(newScope, classMap);
                                            scopedParametersDeclarations.put(newScope, paramMap);
                                            // Push the new Scope into the stack
                                            ScopesStack.push(newScope);
                                            //push the parameters into the scope
                                            for (int i = 0; i < requParams.size(); i++) {
                                                String paramName = requParams.get(i).toString();
                                                Object type = receivedParams.get(i);
                                                if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(paramName)) {
                                                    scopedParametersDeclarations.get(ScopesStack.peek()).put(paramName,
                                                            new HashMap<>() {{
                                                                put("type", new Param() {{
                                                                    setTypeInstnce(type);
                                                                }});
                                                                put("scope", ScopesStack.peek());
                                                            }});
                                                } else {
                                                    Param paramI = (Param) scopedParametersDeclarations.get(ScopesStack.peek()).get(paramName).get("type");
                                                    if (paramI.getTypeInstnce() == null && currCallName.isEmpty()) {
                                                        throw new RuntimeException("Error : Parameter " + paramName + " is already declared ");
                                                    }
                                                }
                                            }
                                        }
                                        returner = visitChildren(currMethod.getCtx());
                                        ScopesStack.pop();
                                        currCallName = "";
                                        }
                                        else if ( CurrFuncName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText()) || currCallName.equals(CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText())) {
                                            if(!CurrFuncName.isEmpty()){
                                                //add the recursive flag
                                                ( ( Method)scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrFuncName).get("type") ).setIsRecursive();
                                            }
                                            return new Param();
                                        }
                                    }
                                    else{
                                    throw new RuntimeException("Error : " + CurrClasName + '.' + ctx.IDENTIFIER().getFirst().getText() + " is not defined");
                                }
                            }
                        }
                    }else {
                        throw new RuntimeException("this directive is not comleted");
                    }
                }
            }
            if (primary instanceof  SuperConstructor){
                if(!CurrClasName.isBlank()){
                    String Father = (String) scopedDeclaredClasses.get(ScopesStack.peek()).get(CurrClasName).getOrDefault("father","");
                    if(Father.isBlank()){
                        throw new RuntimeException("Current class : " + CurrClasName + " does not inherit another");
                    }else if(!scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(Father)){
                        throw new RuntimeException("Father class : " + Father + "is not defined");
                    } else if (!scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(Father + '.' + ((SuperConstructor)primary).getIdentifier())) {
                        throw new RuntimeException("Father super method : " + Father + '.' + ((SuperConstructor)primary).getIdentifier() + "is not defined");
                    }
                    //if it does exist, call the method
                    VisitingSuper = true ;
                    returner = visit((CompiScriptParser.FunctionContext) scopedDeclaredFunctions.get(ScopesStack.peek()).get(Father + '.' + ((SuperConstructor)primary).getIdentifier()).get("ctx"));
                    VisitingSuper = false ;
                }else{
                    if(!CurrVarDefining.isEmpty() && !currInstanceOf.isBlank()) {
                        String Father = (String) scopedDeclaredClasses.get(ScopesStack.peek()).get(currInstanceOf).getOrDefault("father","");
                        if(Father.isBlank()){
                            throw new RuntimeException("Current class : " + currInstanceOf + " does not inherit another");
                        }else if(!scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(Father)){
                            throw new RuntimeException("Father class : " + Father + "is not defined");
                        } else if (!scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(Father + '.' + ((SuperConstructor)primary).getIdentifier())) {
                            throw new RuntimeException("Father super method : " + Father + '.' + ((SuperConstructor)primary).getIdentifier() + "is not defined");
                        }
                        //if it does exist, call the method
                        String LastType = currInstanceOf;
                        currInstanceOf = Father;
                        VisitingSuper = true ;
                        returner = visit((CompiScriptParser.FunctionContext) scopedDeclaredFunctions.get(ScopesStack.peek()).get(Father + '.' + ((SuperConstructor)primary).getIdentifier()).get("ctx"));
                        VisitingSuper = false ;
                        currInstanceOf = LastType;


                    }else{
                        throw new RuntimeException("There is no current class being defined to call a super class");
                    }
                }
            }
            if (primary instanceof Instance){
                Object lastDeclaration = (Instance)primary; //contiene info del nombre de la variable que es la instancia;
                int i = 1;
                int argspointer = 0;
                while(i < ctx.getChildCount()){
                    if(ctx.getChild(i).getText().equals(".") ||
                            ctx.getChild(i).getText().equals(")") ||
                            ctx.getChild(i).getText().equals("[") ||
                            ctx.getChild(i).getText().equals("(") ||
                            ctx.getChild(i).getText().equals("]")
                    ){
                        //the Identifier is the next one so continue
                        i++;
                        continue;
                    }
                    //visit the identifier
                    if( lastDeclaration instanceof  Instance) {
                        //check if its an attribute
                        String attr = ((Instance)lastDeclaration).getLookUpName() +
                                "." + ctx.getChild(i).getText();
                        String method = ((Instance)lastDeclaration).getClasName() +
                                "." + ctx.getChild(i).getText();
                        if(scopedSymbolTable.get(ScopesStack.peek()).containsKey(attr)){
                            lastDeclaration = scopedSymbolTable.get(ScopesStack.peek())
                                    .get(attr).get("type");
                        }
                        //maybe a method from the class ?
                        else if(scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(method)){
                            currCallName = method;
                            CurrClasName = ((Instance)lastDeclaration).getLookUpName();
                            instanceCall = ((Instance)lastDeclaration).getLookUpName();
                            //retrieve the params, they are inside ()
                            //we gonna skip the opening
                            i+=2;
                            Method methodNamer = (Method) scopedDeclaredFunctions.get(ScopesStack.peek()).get(method).get("type");
                            List<String> requParams = (List<String>) scopedDeclaredFunctions.get(ScopesStack.peek()).get(method).get("params");
                            List<Object> receivedParams = new ArrayList<>();
                            while(!ctx.getChild(i).getText().equals(")")){
                                i++;
                            }
                            if(ctx.arguments() != null && !ctx.arguments().isEmpty()){
                                CompiScriptParser.ArgumentsContext arguments = ctx.arguments().get(argspointer);
                                for(int j= 0; j <arguments.getChildCount() ; j+=2){
                                    receivedParams.add(visit(arguments.getChild(j)));
                                }
                            }
                            argspointer++;
                            if(requParams==null && receivedParams.size() > 0){
                                throw new RuntimeException("Error: " +method + " requieres " + 0
                                        + " parameters ," + receivedParams.size() + " found");
                            }

                            if(requParams!=null && requParams.size() != receivedParams.size()){
                                throw new RuntimeException("Error: " +method + " requieres " +requParams.size()
                                        + " parameters ," + receivedParams.size() + " found");
                            }
                            if(requParams != null ) { //avoid calling recursively because a loop
                                // Create new symbol tables for the new scope
                                // Populate new symbol tables from the existing scope
                                Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                                // Insert new scope into the symbol table maps
                                String newScope = Integer.toString((functMap.size() + symbolMap.size() + classMap.size() + paramMap.size()));
                                scopedDeclaredFunctions.put(newScope, functMap);
                                scopedSymbolTable.put(newScope, symbolMap);
                                scopedDeclaredClasses.put(newScope, classMap);
                                scopedParametersDeclarations.put(newScope, paramMap);
                                // Push the new Scope into the stack
                                ScopesStack.push(newScope);
                                //push the parameters into the scope
                                for (int arg = 0; arg < requParams.size(); arg++) {
                                    String paramName = requParams.get(arg).toString();
                                    Object type = receivedParams.get(arg);
                                    if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(paramName)) {
                                        scopedParametersDeclarations.get(ScopesStack.peek()).put(paramName,
                                                new HashMap<>() {{
                                                    put("type", new Param() {{
                                                        setTypeInstnce(type);
                                                    }});
                                                    put("scope", ScopesStack.peek());
                                                }});
                                    } else {
                                        Param paramI = (Param) scopedParametersDeclarations.get(ScopesStack.peek()).get(paramName).get("type");
                                        if (paramI.getTypeInstnce() == null && currCallName.isEmpty()) {
                                            throw new RuntimeException("Error : Parameter " + paramName + " is already declared ");
                                        }
                                    }
                                }
                                lastDeclaration = visitChildren(methodNamer.getCtx());
                                ScopesStack.pop();
                                currCallName = "";
                                CurrClasName = "";
                                instanceCall="";
                            }
                        }
                        else{
                            throw new RuntimeException("Error: " + ((Instance)lastDeclaration).getClasName() + "." + ctx.getChild(i).getText() + " is not defined");
                        }
                    }else{
                        throw new RuntimeException("Error: " + (lastDeclaration != null? (lastDeclaration.getClass().getSimpleName()) : "null" )+ "." + ctx.getChild(i).getText() + " is not defined");
                    }
                    i++;
                }
                returner = lastDeclaration;
            }
        }

        return returner;
    }

    //visit the arguments
    @Override
    public Object visitArguments(CompiScriptParser.ArgumentsContext ctx){
        List<CompiScriptParser.ExpressionContext>  arguments = new ArrayList<>();
        for (int i = 0; i < ctx.getChildCount() ; i++){
            Object child = ctx.getChild(i);
            if( child instanceof CompiScriptParser.ExpressionContext ){
                arguments.add((CompiScriptParser.ExpressionContext) child);
            }else{
                visit(ctx.getChild(i));
            }
        }
        return arguments;
    }

    //new instance
    @Override
    public Object visitInstantiation(CompiScriptParser.InstantiationContext ctx){
        //chek if it exists tho
        if(!scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(ctx.IDENTIFIER().getText())) {
            throw new RuntimeException("Error: Cannot make and Instance from undefinded: " + ctx.IDENTIFIER().getText());
        }

        //check for an init method
        if(!scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(ctx.IDENTIFIER().getText() +".init")){
            if(ctx.arguments() != null) {
                throw new RuntimeException("Error : " + ctx.IDENTIFIER().getText() + " has no arguments to receive");
            }
        }else{
            Map<String,Object> funcMap = scopedDeclaredFunctions.get(ScopesStack.peek()).get(ctx.IDENTIFIER().getText() +".init");
            Object args = funcMap.getOrDefault("params",null);
            List<CompiScriptParser.ExpressionContext> received = new ArrayList<>();
            if(args != null) {
                List<Object> params = new ArrayList<>((Collection) args);
                if(ctx.arguments() !=  null){
                    received = (List<CompiScriptParser.ExpressionContext>) visit(ctx.arguments());
                }
                if(params.size() !=  received.size()) {
                    throw new RuntimeException("Error : " + ctx.IDENTIFIER().getText() + " expected "
                            + params.size()  + " arguments " + (ctx.arguments() == null ? " none" : received.size())
                            + " were given");
                }else{

                    // Create new symbol tables for the new scope
                    // Populate new symbol tables from the existing scope
                    Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    String newScope = Integer.toString((functMap.size() + symbolMap.size()+classMap.size()+paramMap.size()));;
                    // Insert new scope into the symbol table maps
                    scopedDeclaredFunctions.put(newScope, functMap);
                    scopedSymbolTable.put(newScope, symbolMap);
                    scopedDeclaredClasses.put(newScope, classMap);
                    scopedParametersDeclarations.put(newScope,paramMap);
                    // Push the new Scope into the stack
                    ScopesStack.push(newScope);
                    for(int i = 0 ; i < params.size() ; i++){
                        String paramName = params.get(i).toString();
                        Object type = visit(received.get(i));
                        if (!scopedParametersDeclarations.get(ScopesStack.peek()).containsKey(paramName)) {
                            scopedParametersDeclarations.get(ScopesStack.peek()).put(paramName,
                                    new HashMap<>() {{
                                        put("type", new Param(){{setTypeInstnce(type);}});
                                        put("scope", ScopesStack.peek());
                                    }});
                        } else {
                            Param paramI = (Param) scopedParametersDeclarations.get(newScope).get(paramName).get("type");
                            if (paramI.getTypeInstnce() == null) {
                                throw new RuntimeException("Error : Parameter " + paramName + " is already declared ");
                            }
                        }
                    }
                    currInstanceOf = ctx.IDENTIFIER().getText();
                    Method init = (Method)funcMap.get("type");
                    visitChildren(init.getCtx());
                    String lastScope = ScopesStack.pop();
                    currInstanceOf = "";
                    //retrieve all the values generated into the original scope
                    List<Map<String,Map<String,Object>>> lastDeclaredSymbols = findFromOnTable(CurrVarDefining+'.',scopedSymbolTable,lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            value.put("scope",ScopesStack.peek());
                            // Insert into the scoped symbol table
                            scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String,Map<String,Object>>> lastDeclaredFunctions = findFromOnTable(CurrVarDefining+'.',scopedDeclaredFunctions,lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredFun : lastDeclaredFunctions) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredFun.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope",ScopesStack.peek());
                            scopedDeclaredFunctions.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String,Map<String,Object>>> lasDeclaredParams = findFromOnTable(CurrVarDefining+'.',scopedParametersDeclarations,lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lasDeclaredParams) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope",ScopesStack.peek());
                            scopedParametersDeclarations.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String,Map<String,Object>>> lastDeclaredClasses = findFromOnTable(CurrVarDefining+'.',scopedDeclaredClasses,lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredClasses) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope",ScopesStack.peek());
                            scopedDeclaredClasses.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                }
            }
        }
        return new Instance(ctx.IDENTIFIER().getText(),CurrVarDefining);
    }

    //function block
    @Override
    public Object visitBlock(CompiScriptParser.BlockContext ctx) {
        //Generate a new context

        // Create new symbol tables for the new scope
        // Populate new symbol tables from the existing scope
        Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        // Insert new scope into the symbol table maps
        String newScope = Integer.toString((functMap.size() + symbolMap.size()+classMap.size()+paramMap.size()));
        Object lastDeclared = null;
        if(newScope.equals(ScopesStack.peek())) {
            if (!CurrFuncName.isEmpty()) {
                if (((Function) scopedDeclaredFunctions.get(ScopesStack.peek()).get(CurrFuncName).get("type")).getIsRecursive()) {
                    return new Param();
                }
            } else if (!currCallName.isEmpty()) {
                if (((Function) scopedDeclaredFunctions.get(ScopesStack.peek()).get(currCallName).get("type")).getIsRecursive())
                {
                    return new Param();
                }
            }
        }
        scopedDeclaredFunctions.put(newScope, functMap);
        scopedSymbolTable.put(newScope, symbolMap);
        scopedDeclaredClasses.put(newScope, classMap);
        scopedParametersDeclarations.put(newScope, paramMap);
        // Push the new Scope into the stack
        ScopesStack.push(newScope);

        // Visit the child nodes

        for (CompiScriptParser.DeclarationContext child : ctx.declaration()) {
            lastDeclared = visit(child);
        }
        // Pop the Scope after processing{
        String lastScope = ScopesStack.pop();
        //if its inside a var declaration or a class declaration recover all the declared stuff and bring back
        if (!CurrVarDefining.isBlank()) {
            List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrVarDefining + '.', scopedSymbolTable, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lastDeclaredFunctions = findFromOnTable(CurrVarDefining + '.', scopedDeclaredFunctions, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredFun : lastDeclaredFunctions) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredFun.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedDeclaredFunctions.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lasDeclaredParams = findFromOnTable(CurrVarDefining + '.', scopedParametersDeclarations, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lasDeclaredParams) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedParametersDeclarations.get(ScopesStack.peek()).put(key, value);
                }
            }
            List<Map<String, Map<String, Object>>> lastDeclaredClasses = findFromOnTable(CurrVarDefining + '.', scopedDeclaredClasses, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredClasses) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedDeclaredClasses.get(ScopesStack.peek()).put(key, value);
                }
            }
        }if(VisitingSuper && !CurrVarDefining.isBlank() && !currInstanceOf.isEmpty()){
            List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrVarDefining + '.', scopedSymbolTable, lastScope);
            for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                    String key = entry.getKey(); // Extract the key (String)
                    Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                    // Insert into the scoped symbol table
                    value.put("scope", ScopesStack.peek());
                    scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                }
            }
        }
        return lastDeclared;
    }
    /* Manejo de las clases y sus instancias */

    //declaracion de una clase
    @Override
    public Object visitClassDecl(CompiScriptParser.ClassDeclContext ctx) {
        String ClassName = ctx.IDENTIFIER().getFirst().toString();

        String Father = ctx.IDENTIFIER().size() > 1 ? ctx.IDENTIFIER().get(1).toString() : null;
        //chekar que no exista ni en el scope ni que sea clase
        if (!scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(ClassName)) {
            Map<String,Object> inner = new  HashMap<String, Object>();
            inner.put("type",new Class());
            inner.put("scope",ScopesStack.peek());
            this.CurrClasName = ClassName;
            if (Father == null) {inner.put("father",null);}
            else{
                if (!scopedDeclaredClasses.get(ScopesStack.peek()).containsKey(Father)) {
                    throw new RuntimeException("Error: Cannot inherit from undefined :" + Father);
                }else{
                    inner.put("father",Father);
                    //get all the methods from the super class
                    List<Map<String,Map<String,Object>>> lastDeclaredFunctions = findFromOnTable(Father+'.',scopedDeclaredFunctions,ScopesStack.peek());
                    for (Map<String, Map<String, Object>> lastDeclaredFun : lastDeclaredFunctions) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredFun.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            if(!key.replace(Father,ClassName).equals( ClassName+".init")) {
                                value.put("scope", ScopesStack.peek());
                                scopedDeclaredFunctions.get(ScopesStack.peek()).put(key.replace(Father,ClassName), value);
                            }
                        }
                    }
                }
            }

            scopedDeclaredClasses.get(ScopesStack.peek()).put(ClassName, inner);
            // recorrer todo en la declaracion de function ctx.function() para visitar los nodos
            visitChildren(ctx);

            this.CurrClasName = "";
        }else{
            throw new RuntimeException("Error: " + ClassName + " was already defined ");
        }
        return null;
    }

    /*
     * Manejo de Operaciones Lógicas
     * */
    //Manejo de assigments
    @Override
    public Object visitAssignment(CompiScriptParser.AssignmentContext ctx){
        if(ctx.logic_or() != null) {
            visit(ctx.logic_or());
        } else if (ctx.call() != null) {
            if(Objects.equals(ctx.call().getText(), "this") && (!CurrClasName.isBlank() || !CurrVarDefining.isBlank())){//maybe a this for an Atribute?
                String attribute = ctx.getChild(2).getText();
                HashMap<String,Object> attributeMap = new HashMap<>();
                attributeMap.put("scope",ScopesStack.peek());
                if (attribute == null) {
                    System.out.println("Error: Attribute has no NAME");
                }
                Object type = visit(ctx.assignment());
                if(type == null){
                    attributeMap.put("type",new Param());
                }else{
                    attributeMap.put("type",type);
                }
                if (!CurrClasName.isBlank()) {
                    scopedSymbolTable.get(ScopesStack.peek()).put(CurrClasName + "." + attribute, attributeMap);
                }else {
                    scopedSymbolTable.get(ScopesStack.peek()).put(CurrVarDefining + "." + attribute, attributeMap);
                }

                //if its inside a var declaration or a class declaration recover all the declared stuff and bring back
                if(!CurrClasName.isBlank()) {
                    String lastScope = ScopesStack.pop();
                    List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrClasName + '.', scopedSymbolTable, lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope", ScopesStack.peek());
                            scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String, Map<String, Object>>> lastDeclaredFunctions = findFromOnTable(CurrClasName + '.', scopedDeclaredFunctions, lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredFun : lastDeclaredFunctions) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredFun.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope", ScopesStack.peek());
                            scopedDeclaredFunctions.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String, Map<String, Object>>> lasDeclaredParams = findFromOnTable(CurrClasName + '.', scopedParametersDeclarations, lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lasDeclaredParams) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope", ScopesStack.peek());
                            scopedParametersDeclarations.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    List<Map<String, Map<String, Object>>> lastDeclaredClasses = findFromOnTable(CurrClasName + '.', scopedDeclaredClasses, lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredClasses) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope", ScopesStack.peek());
                            scopedDeclaredClasses.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    ScopesStack.push(lastScope);
                } if(VisitingSuper && !CurrVarDefining.isBlank() && !currInstanceOf.isEmpty()){
                    String lastScope = ScopesStack.pop();
                    List<Map<String, Map<String, Object>>> lastDeclaredSymbols = findFromOnTable(CurrVarDefining + '.', scopedSymbolTable, lastScope);
                    for (Map<String, Map<String, Object>> lastDeclaredSymbol : lastDeclaredSymbols) {
                        for (Map.Entry<String, Map<String, Object>> entry : lastDeclaredSymbol.entrySet()) {
                            String key = entry.getKey(); // Extract the key (String)
                            Map<String, Object> value = entry.getValue(); // Extract the value (Map<String, Object>)
                            // Insert into the scoped symbol table
                            value.put("scope", ScopesStack.peek());
                            scopedSymbolTable.get(ScopesStack.peek()).put(key, value);
                        }
                    }
                    ScopesStack.push(lastScope);
                }

            } else if(Objects.equals(ctx.call().getText(), "this") && CurrClasName.isBlank()) {
                throw new RuntimeException("Error: There is no class to define an attribute");
            }else{
                Object res = visit(ctx.call());
                if (res instanceof  Instance){
                    Instance ins = (Instance)res; //contiene info del nombre de la variable que es la instancia
                    if(scopedSymbolTable.get(ScopesStack.peek()).containsKey(ins.getLookUpName() + "." + ctx.IDENTIFIER().getText())){
                        Object newValue = visit(ctx.assignment());
                        scopedSymbolTable.get(ScopesStack.peek()).get(ins.getLookUpName() + "." + ctx.IDENTIFIER().getText()).put("type",newValue);
                    }else {
                        System.out.println("Error : " + ins.getClasName() + "." + ctx.IDENTIFIER().getText() + "is not defined");
                    }
                }
            }
        }else if(ctx.call() == null){ //symple var asignation
            String variableName = ctx.IDENTIFIER().getText();
            Object variableValue = visit(ctx.assignment());
            if(scopedSymbolTable.get(ScopesStack.peek()).containsKey(variableName)){
                scopedSymbolTable.get(ScopesStack.peek()).get(variableName).replace("type",variableValue);
            }else{
                throw new RuntimeException("Error: Cannot assign undeclared variable " + variableName);
            };
        }

        return visitChildren(ctx);
    }

    //Lógica de los OR
    @Override
    public Object visitLogic_or(CompiScriptParser.Logic_orContext ctx) {
        if(ctx.getChildCount() == 1){
            if(ctx.logic_and().size() == 1){
                return visit( ctx.logic_and().getFirst() );
            }else{
                for (CompiScriptParser.Logic_andContext child : ctx.logic_and()) {
                    return visit(child);
                }
            }
        }

        Object variable = visit(ctx.logic_and(0)); // Start with the first logic_and child

        for (int i = 1; i < ctx.getChildCount(); i += 2) { // Skip by 2 to reach each 'or' and its subsequent logic_and
            String operator = ctx.getChild(i).getText(); // Get the operator ('or')

            Object variableTemp = visit(ctx.getChild(i + 1)); // Visit the next logic_and child
            if (variableTemp instanceof Param && variable instanceof Param) { //somehow both are parameters
                variable = true;
                variableTemp = true;
            }
            if (variableTemp instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variableTemp = variable;
                }
                else{
                    variableTemp = innerType;
                }
            }
            if (variable instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variable = variableTemp;
                }
                else{
                    variable = innerType;
                }
            }
            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                if ("or".equals(operator)) {
                    variable = (Boolean) variable || (Boolean) variableTemp;
                }
            } else {
                throw new RuntimeException("Semantic Error: Comparisons do not generate boolean values for logical operations.");
            }
        }
        return variable instanceof Param? true: variable ;
    }

    //Lógica de los AND
    @Override
    public Object visitLogic_and(CompiScriptParser.Logic_andContext ctx) {
        if(ctx.getChildCount() == 1){
            if(ctx.equality().size() == 1){
                return visit( ctx.equality().getFirst() );
            }else{
                for (CompiScriptParser.EqualityContext child : ctx.equality()) {
                    return visit(child);
                }
            }
        }

        Object variable = visit(ctx.equality(0)); // Start with the first equality child

        for (int i = 1; i < ctx.getChildCount(); i += 2) { // Skip by 2 to reach each 'and' and its subsequent equality
            String operator = ctx.getChild(i).getText(); // Get the operator ('and')

            Object variableTemp = visit(ctx.getChild(i + 1)); // Visit the next equality child
            if (variableTemp instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variableTemp = variable;
                }
                else{
                    variableTemp = innerType;
                }
            }
            if (variable instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variable = variableTemp;
                }
                else{
                    variable = innerType;
                }
            }
            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                if ("and".equals(operator)) {
                    variable = (Boolean) variable && (Boolean) variableTemp;
                }
            } else {
                throw new RuntimeException("Semantic Error: Comparisons do not generate boolean values for logical operations.");
            }
        }
        return variable instanceof Param? true: variable ;
    }

    //logica de la igualdad
    @Override
    public Object visitEquality(CompiScriptParser.EqualityContext ctx) {
        if(ctx.getChildCount() == 1){
            if(ctx.comparison().size() == 1){
                return visit( ctx.comparison().getFirst() );
            }else{
                for (CompiScriptParser.ComparisonContext child : ctx.comparison()) {
                    return visit(child);
                }
            }
        }
        // Start by visiting the first comparison child
        Object variable = visit(ctx.comparison(0));
        String currentOperation = "";

        // Iterate through remaining children to evaluate equality operations
        for (int i = 1; i < ctx.getChildCount(); i += 2) { // Skipping by 2: get operator then the next comparison
            currentOperation = ctx.getChild(i).getText(); // '!=' or '=='
            Object variableTemp = visit(ctx.getChild(i + 1));
            if (variableTemp instanceof Param && variable instanceof Param) { //both are params? then use default of True
                variable = true;
                variableTemp = true;
            }
            if (variableTemp instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variableTemp = variable;
                }
                else{
                    variableTemp = innerType;
                }
            }
            if (variable instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variable = variableTemp;
                }
                else{
                    variable = innerType;
                }
            }
            // Check if both are of the same type or if the operation is valid
            if (variableTemp instanceof  Undefined){
                throw new RuntimeException("Semantic Error: Invalid operation; cannot compare an undefined");
            }
            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                variable = Boolean.TRUE;
            } else if ("!=".equals(currentOperation) || "==".equals(currentOperation)) {
                variable = Boolean.TRUE;
            } else if (currentOperation.isEmpty() && variable == null) {
                variable = variableTemp;
            } else if (!currentOperation.isEmpty()) {
                throw new RuntimeException("Semantic Error: Invalid operation; cannot compare different types.");
            }
        }
        return variable instanceof Param? true: variable ;
    }

    //lógica de las comparaciones
    @Override
    public Object visitComparison(CompiScriptParser.ComparisonContext ctx) {
        if(ctx.getChildCount() == 1){
            if(ctx.term().size() == 1){
                return visit( ctx.term().getFirst() );
            }
        }
        String currentOperation = "";
        Object variable = visit(ctx.getChild(0));
        for (int i = 1; i < ctx.getChildCount(); i+=2) {
            Object variableTemp = visit(ctx.getChild(i+1));
            currentOperation = ctx.getChild(i).getText();
            //if somehow both are params using default true value
            if (variableTemp instanceof Param && variable instanceof Param) {
                variable = true;
                variableTemp = true;
            }
            if (variableTemp instanceof Param) {
                Object innerType = (Param) ((Param) variableTemp).getTypeInstnce();
                if(innerType == null) {
                    variableTemp = variable;
                }
                else{
                    variableTemp = innerType;
                }
            }
            if (variable instanceof Param) {
                Object innerType = (Param) ((Param) variable).getTypeInstnce();
                if(innerType == null) {
                    variable = variableTemp;
                }
                else{
                    variable = innerType;
                }
            }
            // Determine if the current child is an operator or a value
            if (variableTemp instanceof  Undefined){
                throw new RuntimeException("Semantic Error: Invalid operation; cannot compare an undefined");
            }
            if (variableTemp.getClass() ==variable.getClass() &&
                    (">".equals(currentOperation) || "<".equals(currentOperation) ||
                            ">=".equals(currentOperation) || "<=".equals(currentOperation))) {
                variable = Boolean.TRUE;
            } else if ("!=".equals(currentOperation) || "==".equals(currentOperation)) {
                variable = Boolean.TRUE;
            } else if (!currentOperation.isEmpty()) {
                throw new RuntimeException("Semantic Error: Invalid operation; cannot compare different types.");
            }
        }
        return variable instanceof Param? true: variable ;
    }

    //while statement
    @Override
    public Object visitWhileStmt(CompiScriptParser.WhileStmtContext ctx) {
        // Visit the expression within the 'while' statement
        Object exprResult = visit(ctx.expression());

        // Ensure the expression results in a Boolean
        if (!(exprResult instanceof Boolean)) {
            throw new RuntimeException("Error : while conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
        }

        // Visit the statement to be executed in the loop
        return visit(ctx.statement());
    }

    //for statement
    @Override
    public Object visitForStmt(CompiScriptParser.ForStmtContext ctx) {
        // Visit the initializer (if present)
        Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> paramMap = new HashMap<>(scopedParametersDeclarations.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        // Insert new scope into the symbol table maps
        String newScope = Integer.toString((functMap.size() + symbolMap.size()+classMap.size()+paramMap.size()));
        scopedDeclaredFunctions.put(newScope, functMap);
        scopedSymbolTable.put(newScope, symbolMap);
        scopedDeclaredClasses.put(newScope, classMap);
        scopedParametersDeclarations.put(newScope, paramMap);
        // Push the new Scope into the stack
        ScopesStack.push(newScope);

        if (ctx.varDecl() != null) {
            visit(ctx.varDecl());
        } else if (ctx.exprStmt() != null) {
            visit(ctx.exprStmt());
        }

        // Visit the condition expression (if present)
        if (ctx.expression(0) != null) {
            Object exprResult = visit(ctx.expression(0));
            if (!(exprResult instanceof Boolean)) {
                throw new RuntimeException("Error : For conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
            }
        }

        // Visit the increment expression (if present)
        if (ctx.expression(1) != null) {
            visit(ctx.expression(1));
        }

        // Visit the statement to be executed in the loop
        Object visitResolution = visit(ctx.statement());

        ScopesStack.pop();
        return visitResolution;
    }

    //if statement
    @Override
    public Object visitIfStmt(CompiScriptParser.IfStmtContext ctx) {
        // Visit the expression within the 'if' statement
        Object exprResult = visit(ctx.expression());

        // Ensure the expression results in a Boolean
        if (!(exprResult instanceof Boolean)) {
            throw new RuntimeException("Error : if conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
        }

        // Visit the 'then' statement
        visit(ctx.statement(0));

        // Visit the 'else' statement if it exists
        if (ctx.statement(1) != null) {
            return visit(ctx.statement(1));
        }

        return null;
    }

    public HashMap<String,Map<String,Object>> getFusedSymbolTable(){
        HashMap<String,Map<String,Object>> fusedST = new HashMap<>();
        scopedSymbolTable.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))){
                    fusedST.put(id,data);
                }
            });
        });
        scopedDeclaredClasses.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))) {
                    fusedST.put(id,data);
                }
            });
        });
        scopedDeclaredFunctions.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))){
                    fusedST.put(id,data);
                }
            });
        });
        scopedParametersDeclarations.forEach((scope,table)->{
            table.forEach((id,data)->{
                if(scope.equals(data.get("scope"))) {
                    fusedST.put(id,data);
                }
            });
        });
        return fusedST;
    }
}