import org.antlr.v4.runtime.tree.TerminalNode;

import javax.lang.model.type.DeclaredType;
import java.util.regex.Pattern;
import java.util.*;

class Function {} //a function
class Class {} // a class , no rocket science
class Undefined {} //variables that only have been declared, but no value assigned
class Method {}  //for methods , functions inside a class
class Unknown {} //for all symbols that were not found
public class CompiScriptCustomVisitor   extends CompiScriptBaseVisitor<Object> {
    //Stack para los contextos
    private Stack<Integer> ScopesStack = new Stack<>(){{push(0);}};
    //the symbol table
    private Map<String,Map<String,Object>> symbolTable = new HashMap<>();
    //Flag para el manejo de declaracion de clases
    private String CurrClasName = null ;
    private String CurrVarDefining = null ;
    // Tabla de símbolos para almacenar funciones
    private Map<String, Object> functions = new HashMap<>();
    // Tabla de símbolos para almacenar variables globales
    private Map<String, HashMap<String, Object>> declaredClasses = new HashMap<>();

    // get a list of all the methods of a class  ( useful for heriachy and inherance )
    public List<String> findMethodsOf(String pattern) {
        Pattern regex = Pattern.compile(pattern); // Compile the regex pattern
        List<String> results = new ArrayList<>(); // To store the "other half" of the method names

        // Iterate over each entry in the symbol table
        for (Map.Entry<String, Map<String, Object>> entry : symbolTable.entrySet()) {
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
            if (symbolTable.containsKey(varName)) {
                return symbolTable.get(varName).get("type");
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
        }/*else if (ctx.getText().equals("this")) {
            return currentInstance;
        } else if (ctx.SUPER() != null && ctx.IDENTIFIER() != null) {
            return lookupSuper(identifier);
        }*/
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
        if (!symbolTable.containsKey(varName) || symbolTable.get(varName).get("scope")!= ScopesStack.peek()) {
            HashMap<String,Object> varMap = new HashMap<>();
            if (ctx.expression() != null) {
                CurrVarDefining = varName;
                Object type = visit(ctx.expression());
                varMap.put("type",type);
                CurrVarDefining = null;
            }else{ //is just defined, no assigmen
                varMap.put("type",new Undefined()); // the new Undefined()
            }
            varMap.put("scope",ScopesStack.peek());
            symbolTable.put(varName, varMap);
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
        functMap.put("returns",null);
        if (CurrClasName.isEmpty()){
            // Guarda la función en la tabla de funciones
            declaredClasses.put(functionName,functMap);
        }else{
            // Guardar en la clase
            if (!CurrClasName.isEmpty() && declaredClasses.containsKey(CurrClasName)) {
                declaredClasses.get(this.CurrClasName).put(functionName, functMap);
            } else {
                // Handle the case where CurrClasName is null or does not exist in declaredClasses
                System.err.println("Error: CurrClasName is null or not found in declaredClasses.");
            }
        }
        return null;
    }

    //Manejo de llamadas
    @Override
    public Object visitCall(CompiScriptParser.CallContext ctx) {
        if (ctx.getChildCount() == 1){ //es una llamada call que termina en una primaria (alguna declaracion posiblemente )
            if(ctx.primary().array() != null) { // ver en caso de una declaracion de array
                return visit(ctx.primary().array());
            }else {
                return visit(ctx.primary());
            }
        }else{
        }
        return null;
    }

    /* Manejo de las clases y sus instancias */


    //declaracion de una clase
    @Override
    public Object visitClassDecl(CompiScriptParser.ClassDeclContext ctx) {
        String ClassName = ctx.IDENTIFIER().getFirst().toString();

        String Father = ctx.IDENTIFIER().size() > 1 ? ctx.IDENTIFIER().get(1).toString() : null;
        //chekar que no exista ni en el scope ni que sea clase
        if (!symbolTable.containsKey(ClassName)
                || symbolTable.get(ClassName).get("scope")!= ScopesStack.peek()
                &&  !(symbolTable.get(ClassName).get("type") instanceof Class)) {

            Map<String,Object> inner = new  HashMap<String, Object>();

            this.CurrClasName = ClassName;
            if (Father == null) {inner.put("father",null);}
            if (Father != null && !symbolTable.containsKey(Father)) {
                System.err.println("Error: Cannot heritate from undefined :" + Father);
            }else{
                if (symbolTable.get(Father).get("scope") == ScopesStack.peek()
                        && symbolTable.get(Father).get("type") instanceof Class
                ) {
                    inner.put("father",Father);
                    //obtener atributos y metodos del padre:

                }else{
                    if(!(symbolTable.get(Father).get("type") instanceof Class)){
                        System.err.println("Error: " + Father +  "is not a class");
                    }
                    if(symbolTable.get(Father).get("scope") != ScopesStack.peek()){
                        System.err.println("Error: " + Father +  "was not found in this scope");
                    }
                }
            }
            symbolTable.put(ClassName, inner);
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
}

