import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;


public class IntermediateCodeVisitor extends  CompiScriptBaseVisitor<Object>{
    //Simple expressions
    private int tempCounter = 0;


    //generates a new temporal pointing to the expression given
    private String newTemp(){
        return "t" + (tempCounter++);
    }

    private List<String> instructions = new ArrayList<>();  // To store TAC instructions

    // Method to get the generated TAC instructions
    public List<String> getInstructions() {
        return instructions;
    }

    // Method to write TAC instructions to a file
    public  void writeToFile(String filePath) {
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

    //returning the primaries:
    @Override
    public Object visitPrimary(CompiScriptParser.PrimaryContext ctx){
        if (ctx.NUMBER() != null) {
            return ctx.NUMBER().getText();  // Return the number
        } else if (ctx.IDENTIFIER() != null) {
            return ctx.IDENTIFIER().getText();  // Return the variable name
        } else if (ctx.expression() != null) {
            return visit(ctx.expression());  // Parenthesized expression
        }
        return "";
    }

    //Aritmetic ( term )  +,-
    @Override
    public  Object visitTerm(CompiScriptParser.TermContext ctx){
        String result = String.valueOf(visit(ctx.factor(0)));
        // If there are additional factors with '+' or '-' operations
        for (int i = 1; i < ctx.factor().size(); i++) {
            String nextFactor = String.valueOf(visit(ctx.factor(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '+' or '-'
            // Generate TAC for the operation
            String temp = newTemp();
            instructions.add(temp + " = " + result + " " + op + " " + nextFactor);
            result = temp;  // The result becomes the new temporary variable
        }

        return result;
    }

    // Visit factor: handles '*' '/' '%'
    @Override
    public Object visitFactor(CompiScriptParser.FactorContext ctx) {
        // Visit the first unary
        String result = String.valueOf(visit(ctx.unary(0)));

        // If there are additional unaries with '*' '/' '%' operations
        for (int i = 1; i < ctx.unary().size(); i++) {
            String nextUnary = String.valueOf(visit(ctx.unary(i)));
            String op = ctx.getChild(2 * i - 1).getText();  // '*' '/' '%'

            // Generate TAC for the operation
            String temp = newTemp();
            instructions.add(temp + " = " + result + " " + op + " " + nextUnary);
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
            instructions.add(temp + " = " + op + " " + operand);
            return temp;
        } else {
            // Otherwise, it's a call, so just visit the call
            return visit(ctx.call());
        }
    }

    //visit var asigment
    @Override
    public Object visitVarDecl(CompiScriptParser.VarDeclContext ctx) {
        String varName = ctx.IDENTIFIER().getText();
        Object val = visit(ctx.expression());
        instructions.add(varName + " = " + val);
        return null;
    };
    // Visit call: handles function calls, array accesses, and identifiers
}
