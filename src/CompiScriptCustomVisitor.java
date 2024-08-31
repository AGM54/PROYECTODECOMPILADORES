import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

public class CompiScriptCustomVisitor extends CompiScriptBaseVisitor<Object> {
    // Tabla de símbolos para almacenar funciones
    private Map<String, CompiScriptParser.FunctionContext> functions = new HashMap<>();
    // Tabla de símbolos para almacenar variables globales
    private Map<String, Object> globalVariables = new HashMap<>();
    // Pila para manejar los diferentes niveles de scope
    private Stack<Map<String, Object>> localScopes = new Stack<>();

    // Visita la declaración de una función
    @Override
    public Object visitFunction(CompiScriptParser.FunctionContext ctx) {
        String functionName = ctx.IDENTIFIER().getText();
        functions.put(functionName, ctx);
        return null;
    }

    // Visita la llamada a una función
    @Override
    public Object visitCall(CompiScriptParser.CallContext ctx) {
        String functionName = ctx.IDENTIFIER().getText();
        CompiScriptParser.FunctionContext function = functions.get(functionName);

        if (function != null) {
            Object[] args = new Object[ctx.arguments().expression().size()];
            for (int i = 0; i < ctx.arguments().expression().size(); i++) {
                args[i] = visit(ctx.arguments().expression(i));
            }
            return callFunction(function, args);
        }

        System.err.println("Error: Función no definida " + functionName);
        return null;
    }

    // Maneja la ejecución de una función
    private Object callFunction(CompiScriptParser.FunctionContext functionCtx, Object[] args) {
        Map<String, Object> localVariables = new HashMap<>();
        localScopes.push(localVariables); // Pushear el nuevo scope a la pila

        for (int i = 0; i < functionCtx.parameters().IDENTIFIER().size(); i++) {
            String paramName = functionCtx.parameters().IDENTIFIER(i).getText();
            localVariables.put(paramName, args[i]);
            System.out.println("Argumento '" + paramName + "' asignado a valor " + args[i] + " en el scope de la función.");
        }

        Object returnValue = null;
        for (CompiScriptParser.DeclarationContext declCtx : functionCtx.block().declaration()) {
            if (declCtx.statement() != null && declCtx.statement().returnStmt() != null) {
                returnValue = visit(declCtx.statement().returnStmt().expression());
                localScopes.pop(); // Limpiar el entorno local después de la ejecución de la función
                return returnValue; // Salir inmediatamente al encontrar un retorno
            } else {
                visit(declCtx);
            }
        }

        localScopes.pop();
        return returnValue;
    }

    // Visita la sentencia print
    @Override
    public Object visitPrintStmt(CompiScriptParser.PrintStmtContext ctx) {
        Object value = visit(ctx.expression());
        System.out.println("Imprimiendo: " + value);
        return null;
    }

    // Visita una expresión de suma
    @Override
    public Object visitTerm(CompiScriptParser.TermContext ctx) {
        if (ctx.getChildCount() == 3) {
            Object left = visit(ctx.factor(0));
            Object right = visit(ctx.factor(1));

            if (left instanceof Double && right instanceof Double) {
                String operator = ctx.getChild(1).getText();
                switch (operator) {
                    case "+":
                        return (Double) left + (Double) right;
                    case "-":
                        return (Double) left - (Double) right;
                }
            }
        }
        return visit(ctx.factor(0));
    }

    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        if (ctx.getChildCount() == 3) {
            Object left = visit(ctx.unary(0));
            Object right = visit(ctx.unary(1));

            if (left instanceof Double && right instanceof Double) {
                String operator = ctx.getChild(1).getText();
                switch (operator) {
                    case "*":
                        return (Double) left * (Double) right;
                    case "/":
                        return (Double) left / (Double) right;
                }
            }
        }
        return visit(ctx.unary(0));
    }

    // Maneja valores primarios (números, etc.)
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx) {
        if (ctx.NUMBER() != null) {
            return Double.valueOf(ctx.NUMBER().getText());
        } else if (ctx.IDENTIFIER() != null) {
            String varName = ctx.IDENTIFIER().getText();
            for (int i = localScopes.size() - 1; i >= 0; i--) {
                Map<String, Object> scope = localScopes.get(i);
                if (scope.containsKey(varName)) {
                    System.out.println("Variable '" + varName + "' encontrada en el scope local con valor " + scope.get(varName));
                    return scope.get(varName);
                }
            }
            if (globalVariables.containsKey(varName)) {
                System.out.println("Variable '" + varName + "' encontrada en el scope global con valor " + globalVariables.get(varName));
                return globalVariables.get(varName);
            } else {
                System.err.println("Error: Variable no definida " + varName);
            }
        }
        return null;
    }

    // Visita una asignación
    @Override
    public Object visitAssignment(CompiScriptParser.AssignmentContext ctx) {
        if (ctx.IDENTIFIER() != null) {
            String varName = ctx.IDENTIFIER().getText();
            Object value = visit(ctx.expression());

            for (int i = localScopes.size() - 1; i >= 0; i--) {
                Map<String, Object> scope = localScopes.get(i);
                if (scope.containsKey(varName)) {
                    scope.put(varName, value);
                    System.out.println("Variable '" + varName + "' actualizada en el scope local con valor " + value);
                    return value;
                }
            }
            globalVariables.put(varName, value);
            System.out.println("Variable '" + varName + "' creada o actualizada en el scope global con valor " + value);
            return value;
        } else {
            return visit(ctx.logic_or());
        }
    }

    // Visita un bloque para crear un nuevo scope
    @Override
    public Object visitBlock(CompiScriptParser.BlockContext ctx) {
        localScopes.push(new HashMap<>());
        System.out.println("Nuevo scope creado.");

        super.visitBlock(ctx);

        localScopes.pop();
        System.out.println("Scope destruido.");
        return null;
    }
}
