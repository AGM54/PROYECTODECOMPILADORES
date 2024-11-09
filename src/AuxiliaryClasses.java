import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Variable {
    public String name;
    public Object value;
    public Variable(String name,Object value){
        this.name = name;
        this.value = value;
    }
}
class Register {
    public String pointer;
    public Object value;
    public Register(String pointer,Object value){
        this.pointer = pointer;
        this.value = value;
    }
}
class Function {
    private  String funName;
    private Boolean isRecursive = false;
    private Object returnsType = null;
    public Function(String funName){
        this.funName = funName;
    }
    public int recursiveInstances = 0;
    public  String getFunName(){
        return this.funName;
    }
    private CompiScriptParser.FunctionContext ctx = null;
    public List<Map<String, Map<String,Object>>> params = new ArrayList<>();
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
    public String pointerRef;
    public void setTypeInstnce(Object typeInstnce) {
        this.typeInstnce = typeInstnce;
    }
    public Object getTypeInstnce() {return typeInstnce;}
} //for the parameters, will be assumed as correct when function is declared