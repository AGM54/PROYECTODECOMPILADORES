
public class IntermediateCodeVisitor extends  CompiScriptBaseVisitor<Object>{
    //Simple expressions
    private int tempCounter = 0;
    //Aritmetic ( term )  +,-
    @Override
    public  Object visitTerm(CompiScriptParser.TermContext ctx){
        String tmpPointer = "t"+tempCounter;
        String operation = "";
        tempCounter++;
        Object result = visit(ctx.factor(0));
        for (int i = 1; i < ctx.factor().size(); i++) {
            Object nextValue = visit(ctx.factor(i));
            if (nextValue.equals("+") || nextValue.equals("-")){

            }
        }
        return null;
    }
}
