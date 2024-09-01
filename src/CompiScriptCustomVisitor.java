import org.antlr.v4.runtime.tree.TerminalNode;
import org.antlr.v4.runtime.tree.TerminalNodeImpl;

import javax.lang.model.type.DeclaredType;
import java.util.regex.Pattern;
import java.util.*;

class Function {
    private CompiScriptParser.FunctionContext ctx = null;

    public void setCtx(CompiScriptParser.FunctionContext ctx) {
        this.ctx = ctx;
    }

    public CompiScriptParser.FunctionContext getCtx() {
        return ctx;
    }
} //a function
class Class {} // a class , no rocket science
class Instance{}
class Undefined {} //variables that only have been declared, but no value assigned
class Method extends Function{}  //for methods , functions inside a class
class Unknown {} //for all symbols that were not found
class ThisDirective{}
class SuperConstructor {}
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
    //Flag para el manejo de declaracion de clases , funciones y variables de tipo Instancia o experesiones
    private String CurrClasName = "" ;
    private String CurrVarDefining = "" ;
    private String CurrFuncName = "";
    private Integer AnonCount = 0; //for all blocks that are on Anon fucntion
    // Tabla de símbolos para almacenar variables globales

    // get a list of all the methods of a class  ( useful for heriachy and inherance )
    public List<String> findMethodsOf(String pattern) {
        Pattern regex = Pattern.compile(pattern); // Compile the regex pattern
        List<String> results = new ArrayList<>(); // To store the "other half" of the method names

        // Iterate over each entry in the symbol table
        for (Map.Entry<String, Map<String, Object>> entry : scopedDeclaredFunctions.get(ScopesStack.peek().toString()).entrySet()) {
            String keyName = entry.getKey(); // First key
            Map<String, Object> attributes = entry.getValue();

            // Check if "type" exists and is an instance of Method
            if (attributes.containsKey("type") && attributes.get("type") instanceof Method) {
                // Check if the key name matches the regex pattern
                if (regex.matcher(keyName).find()) {
                    // Split the keyName by the dot
                    String[] parts = keyName.split("\\.");
                    if (parts.length > 1) {
                        // Add the second part to the results list
                        results.add(parts[1]);
                    }
                }
            }
        }
        return results;
    }
    public void printSymbols() {
        System.out.println("Symbols: ");
        System.out.println("|Name|\t|Scope|\t|Type|\t|Super|\t|Arguments|");
        scopedSymbolTable.forEach((scope,table)->{
            table.forEach((id,data)->{
                if (data.get("scope") == (scope)) {
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + data.getOrDefault("type","Undefined").getClass().getSimpleName() + "|" + data.getOrDefault("father", "") + "|" +
                            data.getOrDefault("params", "")
                    );
                }
            });
        });
        scopedDeclaredClasses.forEach((scope,table)->{
            table.forEach((id,data)->{
                if (data.get("scope") == (scope)) {
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + data.get("type").getClass().getSimpleName() + "|" + data.getOrDefault("father", "") + "|" +
                            data.getOrDefault("params", "")
                    );
                }
            });
        });
        scopedDeclaredFunctions.forEach((s,table)->{
            table.forEach((id,data)->{
                if (data.get("scope") == (s)) {
                    System.out.println("|" + id + "|" + data.get("scope") + "|"
                            + data.get("type").getClass().getSimpleName() + "|" + data.getOrDefault("father","") + "|" +
                            data.getOrDefault("params","")
                    );
                }
            });
        });
    }
    /*
    Sección de manejo de valores atómicos o primarios
     */

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
            }
            else {
                System.err.println("Error: Unknown symbol :" + varName);
                return new Unknown();
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
        }else if (ctx.getText().equals("this")) {
            return new ThisDirective();
        }else if (ctx.getText().equals("super")) {
            return new SuperConstructor();
        } else if (ctx.instantiation() != null) {
            System.out.println("is an instance");
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
                    System.err.println("Unary '-' operator can only be applied to numbers.");
                    return null;
                }
            } else if ("!".equals(operator)) {
                if (value instanceof Boolean) {
                    return !((Boolean) value);
                } else {
                    System.err.println("Unary '!' operator can only be applied to boolean values.");
                    return null;
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
            String operator = ctx.getChild(2 * i - 1).getText();

            if (operator.equals("+")) {
                if (result instanceof Number && nextValue instanceof Number) {
                    if (result instanceof Double || nextValue instanceof Double) { // any with double = a double no matter is its int + double
                        result = ((Number) result).doubleValue() + ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() + ((Number) nextValue).intValue();
                    }
                } else if (result instanceof String && nextValue instanceof String) {
                    result = (String) result + (String) nextValue; // String concatenation
                } else {
                    System.err.println("Operands must be both numbers or both strings for '+' operation. , found: "+ result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
                }
            } else if (operator.equals("-")) {
                if (result instanceof Number && nextValue instanceof Number) {
                    if (result instanceof Double || nextValue instanceof Double) {
                        result = ((Number) result).doubleValue() - ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() - ((Number) nextValue).intValue();
                    }
                } else {
                    System.err.println("Operands must be numbers for substraction '-' operation. " + result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
                }
            }
        }
        return result;
    }
    //modulo, multiplicacion, division (factor)
    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        Object result = visit(ctx.unary(0));

        for (int i = 1; i < ctx.unary().size(); i++) {
            Object nextValue = visit(ctx.unary(i));
            String operator = ctx.getChild(2 * i - 1).getText();

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
                        System.err.println("Unknown operator: " + operator);
                }
            } else {
                System.err.println("Operands must be numbers for '*' '/' '%' operations. found: " + result.getClass().getSimpleName() + " , " + nextValue.getClass().getSimpleName());
            }
        }

        return result; //siempre se regresa el unario de no ser que no hayan más , caso opuesto dictamina el return type en estas operaciones
    }

    /*Manejo de Variables*/

    // declaracion de variables
    @Override
    public Object visitVarDecl(CompiScriptParser.VarDeclContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        if (varName == null || varName.isBlank() || varName.isEmpty()) {
            System.err.println("Error: Var must have an identifier");
        }
        //check if is not defined or if is not defined on this scope
        if (!scopedSymbolTable.get(ScopesStack.peek().toString()).containsKey(varName)) {
            CurrClasName = varName;
            HashMap<String,Object> varMap = new HashMap<>();
            if (ctx.expression() != null) {
                CurrVarDefining = varName;
                Object type = visit(ctx.expression());
                CurrClasName = "";
                varMap.put("type",type);
                CurrVarDefining = null;
            }else{ //is just defined, no assigmen
                varMap.put("type",new Undefined()); // the new Undefined()
            }
            varMap.put("scope",ScopesStack.peek());
            scopedSymbolTable.get(ScopesStack.peek().toString()).put(varName, varMap);
        }else{
            System.err.println("Error: Variable already defined " + varName);
        }
        return null;
    }


    /*Manejo de Funciones*/

    // Visita la declaración de una función
    @Override
    public Object visitFunction(CompiScriptParser.FunctionContext ctx) {
        // Obtén el nombre de la función del primer identificador en la lista
        String functionName = ctx.IDENTIFIER().getText();
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
            functMap.put("type",new Function(){{setCtx(ctx);}});
            if(!scopedDeclaredFunctions.get(ScopesStack.peek().toString()).containsKey(functionName)) {
                scopedDeclaredFunctions.get(ScopesStack.peek().toString()).put(functionName, functMap);
            }else{
                System.err.println("Error: Function already defined" + functionName);
            }
        }else{
            // Guardar en la clase
            if (scopedDeclaredClasses.get(ScopesStack.peek().toString()).containsKey(CurrClasName)) {
                if(!scopedDeclaredFunctions.get(ScopesStack.peek().toString()).containsKey(CurrClasName + "." + functionName)){
                    functMap.put("type",new Method(){{setCtx(ctx);}});
                    scopedDeclaredFunctions.get(ScopesStack.peek().toString()).put(CurrClasName + "." + functionName,functMap);;
                }else{
                    System.err.println("Error: Method already defined" + CurrClasName + "." + functionName);
                }

            } else {
                // Handle the case where CurrClasName is null or does not exist in declaredClasses
                System.err.println("Error: " + CurrClasName + " found in declaredClasses.");
            }
        }
        CurrFuncName = functionName;
        visitChildren(ctx);
        CurrFuncName = "";
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
        if (ctx.getChildCount() == 1){ //primary call
            if(ctx.primary().array() != null) { // is an array (somehow)
                return visit(ctx.primary().array());
            }else { //just a primary
                return visit(ctx.primary());
            }
        }else if (ctx.primary().instantiation().getChildCount()> 0){ //create an instance
            System.out.println("is an instance");
            String nombreClase = ctx.primary().instantiation().getChild(1).getText();
            System.out.println(nombreClase);

        }
        return null;
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
            System.err.println("Error: Cannot make and Instance from undefinded: " + ctx.IDENTIFIER().getText());
        }
        //check for an init method
        if(!scopedDeclaredFunctions.get(ScopesStack.peek()).containsKey(ctx.IDENTIFIER().getText() +".init")){
            if(ctx.arguments() != null) {
                System.err.println("Error : " + ctx.IDENTIFIER().getText() + " has no arguments to receive");
            }
        }else{
            Map<String,Object> funcMap = scopedDeclaredFunctions.get(ScopesStack.peek()).get(ctx.IDENTIFIER().getText() +".init");
            Object args = funcMap.getOrDefault("params",null);
            if(args != null) {
                List<Object> params = new ArrayList<>((Collection) args);
                List<CompiScriptParser.ExpressionContext> received = (List<CompiScriptParser.ExpressionContext>) visit(ctx.arguments());
                System.out.println(received);
                if(ctx.arguments() == null || params.size() !=  received.size()) {
                    System.err.println("Error : " + ctx.IDENTIFIER().getText() + " expected "
                            + params.size()  + " arguments " + (ctx.arguments() == null ? " none" : received.size())
                            + " were given");
                }else{
                    String newScope = CurrVarDefining;
                    // Create new symbol tables for the new scope
                    // Populate new symbol tables from the existing scope
                    Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
                    Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));

                    // Insert new scope into the symbol table maps
                    scopedDeclaredFunctions.put(newScope, functMap);
                    scopedSymbolTable.put(newScope, symbolMap);
                    scopedDeclaredClasses.put(newScope, classMap);

                    // Push the new Scope into the stack
                    ScopesStack.push(newScope);
                    for(int i = 0 ; i < params.size() ; i++){
                        String paramName = params.get(i).toString();
                        Object type = visit(received.get(i));
                    }
                    Method init = (Method)funcMap.get("type");
                    visit(init.getCtx());
                    ScopesStack.pop();
                }
            }
        }
        return new Instance();
    }
    //function block
    @Override
    public Object visitBlock(CompiScriptParser.BlockContext ctx) {
        // Generate a new context
        String newScope;
        if (!CurrClasName.isEmpty()) {
            if (!CurrFuncName.isEmpty()) {
                newScope = CurrClasName + "." + CurrFuncName;
            } else {
                newScope = CurrClasName;
            }
        } else if (!CurrFuncName.isEmpty()) {
            newScope = CurrFuncName;
        } else {
            newScope = "Anon" + AnonCount;
            AnonCount++;
        }

        // Create new symbol tables for the new scope
        // Populate new symbol tables from the existing scope
        Map<String, Map<String, Object>> functMap = new HashMap<>(scopedDeclaredFunctions.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> symbolMap = new HashMap<>(scopedSymbolTable.getOrDefault(ScopesStack.peek(), new HashMap<>()));
        Map<String, Map<String, Object>> classMap = new HashMap<>(scopedDeclaredClasses.getOrDefault(ScopesStack.peek(), new HashMap<>()));

        // Insert new scope into the symbol table maps
        scopedDeclaredFunctions.put(newScope, functMap);
        scopedSymbolTable.put(newScope, symbolMap);
        scopedDeclaredClasses.put(newScope, classMap);

        // Push the new Scope into the stack
        ScopesStack.push(newScope);

        // Visit the child nodes
        Object lastDeclared = null;
        for (CompiScriptParser.DeclarationContext child : ctx.declaration()) {
            lastDeclared = visit(child);
        }

        // Pop the Scope after processing
        ScopesStack.pop();

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
                    System.err.println("Error: Cannot inherit from undefined :" + Father);
                }
            }

            scopedDeclaredClasses.get(ScopesStack.peek()).put(ClassName, inner);
            // recorrer todo en la declaracion de function ctx.function() para visitar los nodos
            for (CompiScriptParser.FunctionContext child : ctx.function()){
                visit(child);
            }
            this.CurrClasName = "";
        }else{
            System.err.println("Error: " + ClassName + " was already defined ");
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
            if(Objects.equals(ctx.call().getText(), "this") && !CurrClasName.isBlank()){//maybe a this for an Atribute?
                String attribute = ctx.getChild(2).getText();
                HashMap<String,Object> attributeMap = new HashMap<>();
                attributeMap.put("scope",ScopesStack.peek());
                if (attribute == null) {
                    System.out.println("Error: Attribute has no NAME");
                }
                Object type = visit(ctx.assignment());
                if(type == null){
                    attributeMap.put("type",new Undefined());
                }else{
                    attributeMap.put("type",type);
                }

                scopedSymbolTable.get(ScopesStack.peek()).put(CurrClasName+"."+attribute, attributeMap);
            } else if (Objects.equals(ctx.call().getText(), "this") && CurrClasName.isBlank()) {
                System.out.println("Error: There is no class to define an attribute");
            } else if(ctx.call() == null){ //symple var asignation
                String variableName = ctx.IDENTIFIER().getText();
                Object variableValue = visit(ctx.assignment());
                if(scopedSymbolTable.get(ScopesStack.peek()).containsKey(variableName)){
                    scopedSymbolTable.get(ScopesStack.peek()).get(variableName).replace("type",variableValue);
                }else{
                    System.err.println("Error: Cannot assign undeclared variable " + variableName);
                };
            }
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

            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                if ("or".equals(operator)) {
                    variable = (Boolean) variable || (Boolean) variableTemp;
                }
            } else {
                System.err.println("Semantic Error: Comparisons do not generate boolean values for logical operations.");
            }
        }
        return variable;
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

            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                if ("and".equals(operator)) {
                    variable = (Boolean) variable && (Boolean) variableTemp;
                }
            } else {
                System.err.println("Semantic Error: Comparisons do not generate boolean values for logical operations.");
            }
        }
        return variable;
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

            // Check if both are of the same type or if the operation is valid
            if (variableTemp instanceof  Undefined){
                System.err.println("Semantic Error: Invalid operation; cannot compare an undefined");
            }
            if (variableTemp instanceof Boolean && variable instanceof Boolean) {
                variable = Boolean.TRUE;
            } else if ("!=".equals(currentOperation) || "==".equals(currentOperation)) {
                variable = Boolean.TRUE;
            } else if (currentOperation.isEmpty() && variable == null) {
                variable = variableTemp;
            } else if (!currentOperation.isEmpty()) {
               System.err.println("Semantic Error: Invalid operation; cannot compare different types.");
            }
        }
        return variable;
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
            // Determine if the current child is an operator or a value
            if (variableTemp instanceof  Undefined){
                System.err.println("Semantic Error: Invalid operation; cannot compare an undefined");
            }
            if (variableTemp.getClass() ==variable.getClass() &&
                    (">".equals(currentOperation) || "<".equals(currentOperation) ||
                    ">=".equals(currentOperation) || "<=".equals(currentOperation))) {
                variable = Boolean.TRUE;
            } else if ("!=".equals(currentOperation) || "==".equals(currentOperation)) {
                variable = Boolean.TRUE;
            } else if (!currentOperation.isEmpty()) {
                System.err.println("Semantic Error: Invalid operation; cannot compare different types.");
            }
        }
        return variable;
    }
    @Override
    public Object visitWhileStmt(CompiScriptParser.WhileStmtContext ctx) {
        // Visit the expression within the 'while' statement
        Object exprResult = visit(ctx.expression());

        // Ensure the expression results in a Boolean
        if (!(exprResult instanceof Boolean)) {
            System.err.println("Error : while conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
        }

        // Visit the statement to be executed in the loop
        return visit(ctx.statement());
    }

    @Override
    public Object visitForStmt(CompiScriptParser.ForStmtContext ctx) {
        // Visit the initializer (if present)
        if (ctx.varDecl() != null) {
            visit(ctx.varDecl());
        } else if (ctx.exprStmt() != null) {
            visit(ctx.exprStmt());
        }

        // Visit the condition expression (if present)
        if (ctx.expression(0) != null) {
            Object exprResult = visit(ctx.expression(0));
            if (!(exprResult instanceof Boolean)) {
                System.err.println("Error : For conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
            }
        }

        // Visit the increment expression (if present)
        if (ctx.expression(1) != null) {
            visit(ctx.expression(1));
        }

        // Visit the statement to be executed in the loop
        return visit(ctx.statement());
    }

    @Override
    public Object visitIfStmt(CompiScriptParser.IfStmtContext ctx) {
        // Visit the expression within the 'if' statement
        Object exprResult = visit(ctx.expression());

        // Ensure the expression results in a Boolean
        if (!(exprResult instanceof Boolean)) {
            System.err.println("Error : if conditional -> " + exprResult.getClass().getSimpleName() + " " + exprResult.toString() + " is not boolean order");
        }

        // Visit the 'then' statement
        visit(ctx.statement(0));

        // Visit the 'else' statement if it exists
        if (ctx.statement(1) != null) {
            visit(ctx.statement(1));
        }

        return null;
    }



}

