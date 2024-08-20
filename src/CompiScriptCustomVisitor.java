import java.util.HashMap;
import java.util.Map;

public class CompiScriptCustomVisitor   extends CompiScriptBaseVisitor<Object> {
    // Tabla de símbolos para almacenar funciones
    private Map<String, CompiScriptParser.FunctionContext> functions = new HashMap<>();
    // Tabla de símbolos para almacenar variables globales
    private Map<String, Object> globalVariables = new HashMap<>();
    // Entorno local para las variables dentro de una función
    private Map<String, Object> localVariables = null;

    // Visita la declaración de una función
    @Override
    public Object visitFunction(CompiScriptParser.FunctionContext ctx) {
        // Obtén el nombre de la función del primer identificador en la lista
        String functionName = ctx.IDENTIFIER().getText();
        // Guarda la función en la tabla de funciones
        functions.put(functionName, ctx);
        return null;
    }

    // Visita la llamada a una función

    //@Override
    /*
    public Object visitCall(CompiScriptParser.CallContext ctx) {

        String functionName = ctx.IDENTIFIER().getText();
        CompiScriptParser.FunctionContext function = functions.get(functionName);

        if (function != null) {
            // Evaluar los argumentos
            Object[] args = new Object[ctx.arguments().expression().size()];
            for (int i = 0; i < ctx.arguments().expression().size(); i++) {
                args[i] = visit(ctx.arguments().expression(i));
            }

            // Llamar a la función con los argumentos
            return callFunction(function, args);
        }

        System.err.println("Error: Función no definida " + functionName);
        return null;
    }*/

    // Maneja la ejecución de una función
    private Object callFunction(CompiScriptParser.FunctionContext functionCtx, Object[] args) {
        // Crear un nuevo entorno local para las variables de la función
        localVariables = new HashMap<>();  // Reiniciar el entorno local

        // Asignar argumentos a las variables correspondientes en el ámbito local
        for (int i = 0; i < functionCtx.parameters().IDENTIFIER().size(); i++) {
            String paramName = functionCtx.parameters().IDENTIFIER(i).getText();
            localVariables.put(paramName, args[i]);
        }

        // Ejecutar el bloque de la función y capturar posibles retornos
        Object returnValue = null;
        for (CompiScriptParser.DeclarationContext declCtx : functionCtx.block().declaration()) {
            if (declCtx.statement() != null && declCtx.statement().returnStmt() != null) {
                // Si es una sentencia de retorno, evaluarla y devolver su valor
                returnValue = visit(declCtx.statement().returnStmt().expression());
                localVariables = null;  // Limpiar el entorno local después de la ejecución de la función
                return returnValue;  // Salir inmediatamente al encontrar un retorno
            } else {
                visit(declCtx);  // Visitar otras declaraciones
            }
        }

        // Limpiar el entorno local después de la ejecución de la función
        localVariables = null;
        return returnValue;
    }

    // Visita la sentencia print
    @Override
    public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
        // Evalúa la expresión y la imprime
        Object value = visit(ctx.expression());
        System.out.println(value);
        return null;
    }

    // Visita una expresión de suma o resta (term)
    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        Object result = visit(ctx.factor(0));

        for (int i = 1; i < ctx.factor().size(); i++) {
            Object nextValue = visit(ctx.factor(i));
            String operator = ctx.getChild(2 * i - 1).getText();

            if (operator.equals("+")) {
                if (result instanceof Number && nextValue instanceof Number) {
                    if (result instanceof Double || nextValue instanceof Double) {
                        result = ((Number) result).doubleValue() + ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() + ((Number) nextValue).intValue();
                    }
                } else if (result instanceof String && nextValue instanceof String) {
                    result = (String) result + (String) nextValue; // String concatenation
                } else {
                    System.err.println("Operands must be both numbers or both strings for '+' operation.");
                }
            } else if (operator.equals("-")) {
                if (result instanceof Number && nextValue instanceof Number) {
                    if (result instanceof Double || nextValue instanceof Double) {
                        result = ((Number) result).doubleValue() - ((Number) nextValue).doubleValue();
                    } else {
                        result = ((Number) result).intValue() - ((Number) nextValue).intValue();
                    }
                } else {
                    System.err.println("Operands must be numbers for substraction '-' operation.");
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
                System.err.println("Operands must be numbers for '*' '/' '%' operations.");
            }
        }

        return result; //siempre se regresa el unario de no ser que no hayan más , caso opuesto dictamina el return type en estas operaciones
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
                }
            } else if ("!".equals(operator)) {
                if (value instanceof Boolean) {
                    return !((Boolean) value);
                } else {
                    System.err.println("Unary '!' operator can only be applied to boolean values.");
                }
            }
        }

        // If it's not a unary operation, just visit the call (the next production rule)
        return visit(ctx.call());
    }
    // Maneja valores primarios (números, etc.)
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
        if (ctx.NUMBER() != null) {
            return Double.valueOf(ctx.NUMBER().getText());
        } else if (ctx.IDENTIFIER() != null) {
            // Si es un identificador, busca primero en las variables locales, luego en las globales
            String varName = ctx.IDENTIFIER().getText();
            if (localVariables != null && localVariables.containsKey(varName)) {
                return localVariables.get(varName);
            } else if (globalVariables.containsKey(varName)) {
                return globalVariables.get(varName);
            } else {
                System.err.println("Error: Variable no definida " + varName);
            }
        }
        return null; // Manejar otros tipos si es necesario
    }


}

