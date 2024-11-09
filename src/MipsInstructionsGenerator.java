import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class MipsInstructionsGenerator {


    //Maps
    private HashMap<String,Object> registerDescription = new HashMap();
    private HashMap<String,String> StringConstants = new HashMap();
    //Stacks
    private List<String> jumpCalls = new ArrayList<>();  //functions/methods to be called
    private List<String> mainCalls = new ArrayList<>(){{add(".text");
        add(".globl main");
        add("main:");}};  //the main flow of instructions
    private List<String> dataHeader = new ArrayList<>(){{add(".data");}};  //header for all the .data, will be used to store constant strings also
    private List<String> instructions = mainCalls;  // the pointer into where is written

    //Counters
    private int StackPointer = 0;  // global pointer to the $sp register on mips
    private int tempCounter = 0;
    private int saveCounter = 0;
    private int labelCount = 0; // Unique label counter
    private int tabCounter = 0;
    private int constantCounter = 0;
    private int argsCounter = 0;
    //flags
    private Boolean hasStringBuffer = false;

    public Register zero = new Register("$zero",0);

    private Stack<String> tempPool = new Stack<>();
    private Stack<String> savePool = new Stack<>();
    private Stack<String> jumpLabels = new Stack<>();
    private Stack<String> jumpInverseLabels = new Stack<>();

    public void tabsIncrease(){
        tabCounter++;
    }
    public void tabsDecrease(){
        tabCounter--;
    }

    public List<String> getInstructions ()
    {
        return instructions;
    }
    //create a $t register
    public Register createTemporal(Object val){
        String tmp;
        if (!tempPool.isEmpty()){
            tmp = tempPool.pop();
        }else{
            if (tempCounter < 8) {
                tmp = "$t" + (tempCounter++);
            } else{
                //if somehow whe need more than 8 temporals , we are going to use the stack
                tmp = StackPointer + "($sp)";
                StackPointer += calculateSize(val);
            }
        }
        registerDescription.put(tmp,val);
        return new Register(tmp,val);
    };
    public Register createArgument(Object val){
        String tmp;
        if (argsCounter < 4) {
            tmp = "$a" + (argsCounter++);
        } else{
            //is a lot of arguments D:
            tmp = StackPointer + "($sp)";
            StackPointer += calculateSize(val);
        }
        registerDescription.put(tmp,val);
        return new Register(tmp,val);
    }
    public void resetArguments(){
        argsCounter = 0;
    }
    //to load the $s registers
    public Register createSave(Object val){
        String tmp;
        if (!savePool.isEmpty()){
            tmp = savePool.pop();
        }else{
            if (saveCounter < 8) {
                tmp = "$s" + (saveCounter++);
            } else{
                //if somehow whe need more than 8 temporals , we are going to use the stack
                tmp = StackPointer + "($sp)";
                StackPointer += calculateSize(val);
            }
        }
        registerDescription.put(tmp,val);
        return new Register(tmp,val);
    };
    //free up a temporal
    public void releaseRegister(String register) {
        if(register.startsWith(("$t"))){
            tempPool.push(register);  // Return the temporary to the pool when done
        }
        registerDescription.put(register,null);
    }

    //set up a register
    public Register setRegister(String register,Object val){
        registerDescription.put(register,val);
        return new Register(register,val);
    } ;

    //get the register with the value
    public Register getRegister(Object val){
        if (registerDescription.containsValue(val)){
            for (Map.Entry<String, Object> entry : registerDescription.entrySet()) {
                if(entry.getValue() == null){
                    continue;
                }
                if (entry.getValue().equals(val)) {
                    return new Register(entry.getKey(),val);
                }
            }
        }
        return null;
    } ;

    public Object getRegisterValue(String register){
        return registerDescription.get(register);
    }

    //auxiliaryMethods
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
    public void SwitchToMain(){
        this.instructions = this.mainCalls;
    }
    public void SwitchToLocal(){
        this.instructions = this.jumpCalls;
    }

    public void addStringConstant(String string){
        if(!StringConstants.containsKey(string)){
            dataHeader.add("_S"+ + constantCounter + "_ : .asciiz " + string);
            StringConstants.put(string,("_S"+ + constantCounter + "_"));
            constantCounter++;
        }
    }

    public String getConstantLabel(String string){
        if(!StringConstants.containsKey(string)){
            return "";
        }
        return StringConstants.get(string);
    }

    public int calculateSize (Object type){
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
            case "Variable":
                return calculateSize(((Variable) type).value);
            default:
                throw new IllegalArgumentException("Unsupported type: " + type.getClass().getSimpleName());
        }
    }

    public void addJump(String lbl){
        jumpLabels.push(lbl);
    }
    public void addInverse(String lbl){
        jumpInverseLabels.push(lbl);
    }
    public String quitJump(){
        return jumpLabels.pop();
    }
    public String quitInverse(){
        return jumpInverseLabels.pop();
    }

    //mips instructions
    public String generateLabel() {
        return "L_" + (labelCount++);
    }
    public void pushLabel(String lbl){
        instructions.add("\t".repeat(tabCounter) + lbl + ":");
    }
    public void saveIntoData(String name, Object value, Boolean isAttribute){
        Object initialValue = value;
        String javaType = value.getClass().getSimpleName();
        switch (javaType) {
            case "Integer":
                if (isAttribute) {
                    dataHeader.add(".word " + initialValue);
                } else {
                    dataHeader.add(name + ": .word " + initialValue);
                }
                break;
            case "Float":
                if (isAttribute) {
                    dataHeader.add(".float " + initialValue);
                } else {
                    dataHeader.add(name + ": .float " + initialValue);
                }
                break;
            case "Double":
                if (isAttribute) {

                } else {
                    dataHeader.add(".double " + initialValue);
                }
                break;
            case "Character":
                if (isAttribute) {
                    dataHeader.add(".byte '" + initialValue + "'");
                } else {
                    dataHeader.add(name + ": .byte '" + initialValue + "'");
                }
                break;
            case "Boolean":
                if (isAttribute) {
                    dataHeader.add(".byte " + ((Boolean) initialValue ? "1" : "0"));
                } else {
                    dataHeader.add(name + ": .byte " + ((Boolean) initialValue ? "1" : "0"));
                }
                break;
            case "String":
                if (isAttribute) {
                    dataHeader.add(".asciiz \"" + initialValue + "\"");
                } else {
                    dataHeader.add(name + ": .asciiz \"" + initialValue + "\"");
                }
                break;
            case "Variable":
                saveIntoData(name, ((Variable) initialValue).value, isAttribute);
                break;
            default:
                throw new IllegalArgumentException("Unsupported type: " + javaType);
        }
    }
    public void saveIntoData(String name){
        dataHeader.add(name + ":");
    }

    //loading into registers
    public void loadInmediate(String pointer,Object val){
        instructions.add("\t".repeat(tabCounter) + "li " + pointer +  " , " + val);
    };
    public void loadWord(String pointer,Object val){
        instructions.add("\t".repeat(tabCounter) + "lw " + pointer +  " , " + val);
    };
    public void loadAddres(String pointer,Object val){
        instructions.add("\t".repeat(tabCounter) + "la " + pointer +  " , " + val);
    };
    public void moveInto(String destination,String source){
        instructions.add("\t".repeat(tabCounter) + "move " + destination +  " , " + source);
    };
    public void saveWordInto(String destination,String source){
        instructions.add("\t".repeat(tabCounter) + "sw " + source +  " , " + destination);
    }

    // aritmetic functions
    public void sum(Register save, Object left , Object right ){
        if(left instanceof  Number && right instanceof Number){
            Register leftSide = createTemporal(left);
            loadInmediate(leftSide.pointer , left);
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + ", " + left + ", " + right);
            releaseRegister(leftSide.pointer);
        }else if(left instanceof  Number && right instanceof Register r){
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + ", " + r.pointer + ", " + left);
        } else if (right instanceof Number) {
            assert left instanceof Register;
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + ", " + ((Register) left).pointer + ", " + right);
        }else {
            assert left instanceof Register;
            assert right instanceof Register;
            instructions.add("\t".repeat(tabCounter) + "add " + save.pointer + ", " +
                    ((Register) left).pointer + ", " + ((Register) right).pointer);
        }
    }
    public void subs(Register save, Object left , Object right ){
        if(left instanceof  Number && right instanceof Number){
            Register leftSide = createTemporal(left);
            loadInmediate(leftSide.pointer , left);
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + ", " + left + ", -" + right);
            releaseRegister(leftSide.pointer);
        }else if(left instanceof  Number && right instanceof Register r){
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + ", " + r.pointer + ", -" + left);
        } else if (right instanceof Number) {
            assert left instanceof Register;
            instructions.add("\t".repeat(tabCounter) + "addi " + save.pointer + " , " + ((Register) left).pointer + " , -" + right);
        }else {
            assert left instanceof Register;
            assert right instanceof Register;
            instructions.add("\t".repeat(tabCounter) + "sub " + save.pointer + ", " +
                    ((Register) left).pointer + ", " + ((Register) right).pointer);
        }
    }
    public void mult(Register save, Object left , Object right ){
        if (right instanceof Number) {
            right = createTemporal(right);
            releaseRegister(((Register) right).pointer);
        }
        if (left instanceof Number) {
            left = createTemporal(left);
            releaseRegister(((Register) left).pointer);
        }
        instructions.add("\t".repeat(tabCounter) + "mul " + save.pointer + ", " +
                ((Register) left).pointer + ", " + ((Register) right).pointer);
    }
    public void div(Register save, Object left , Object right ){
        if (right instanceof Number) {
            right = createTemporal(right);
            releaseRegister(((Register) right).pointer);
        }
        if (left instanceof Number) {
            left = createTemporal(left);
            releaseRegister(((Register) left).pointer);
        }
        assert left instanceof Register;
        assert right instanceof Register;
        instructions.add("\t".repeat(tabCounter) + "div " +
                ((Register) left).pointer + ", " + ((Register) right).pointer);
        instructions.add("\t".repeat(tabCounter) + "mflo " + save.pointer); // Move quotient to save
    }
    public void mod(Register save, Object left , Object right ){
        if (right instanceof Number) {
            right = createTemporal(right);
            releaseRegister(((Register) right).pointer);
        }
        if (left instanceof Number) {
            left = createTemporal(left);
            releaseRegister(((Register) left).pointer);
        }
        assert left instanceof Register;
        assert right instanceof Register;
        instructions.add("\t".repeat(tabCounter) + "div " +
                ((Register) left).pointer + ", " + ((Register) right).pointer);
        instructions.add("\t".repeat(tabCounter) + "mfhi " + save.pointer); // Move quotient to save
    }



    //flow control methods
    public void jumpTo(String destination){
        instructions.add("\t".repeat(tabCounter) + "j " + destination);
    }
    public void jumpReturn (){
        instructions.add("\t".repeat(tabCounter) + "jr" + " " + "$ra");
    }
    public void jumpAndLink(String destination){
        instructions.add("\t".repeat(tabCounter) + "jal " + destination);
    }
    public void lessThan(String save,String left, String right){
        instructions.add("\t".repeat(tabCounter)  + "slt" + " " + save + " , " +left + " , " + right);
    }

    public void equals(String left, String right){
        instructions.add("\t".repeat(tabCounter)  + "beq" +left + " , " + right + " , " + jumpLabels.peek());
        if(!jumpInverseLabels.isEmpty()){
            instructions.add("\t".repeat(tabCounter)  + "bne" +left + " , " + right + " , " + jumpInverseLabels.peek());
        }
    }
    public void notEquals(String left, String right){
        instructions.add("\t".repeat(tabCounter)  + "bne" +left + " , " + right + " , " + jumpLabels.peek());
        if(!jumpInverseLabels.isEmpty()){
            instructions.add("\t".repeat(tabCounter)  + "beq" +left + " , " + right + " , " + jumpInverseLabels.peek());
        }
    }

    public Register setReturnRegister(Object val){
        setRegister("$v0",val);
        return  new Register("$v0",val);
    }

    //stack methods
    public void reserveOnStack(int size){
        instructions.add("\t".repeat(tabCounter) + "sub $sp, $sp, " + size);
        StackPointer += size;
    }
    public void releaseOnStack(int size){
        instructions.add("\t".repeat(tabCounter) + "add $sp, $sp, " + size);
        StackPointer -= size;
    }

    public Register saveIntoStack(String source){
        String stackPointer = StackPointer + "($sp)";
        moveInto(stackPointer, source);
        StackPointer+= calculateSize(getRegisterValue(source));
        return new Register(stackPointer,getRegisterValue(source));
    }
    public Register saveIntoStack(String source,Object value){
        String stackPointer = StackPointer + "($sp)";
        moveInto(stackPointer, source);
        StackPointer+= calculateSize(value);
        return new Register(stackPointer,value);
    }

    public void PrintValue(Object val, String refPoint){
        if(val instanceof Variable tmp){
            Register ref = getRegister(tmp);
            if(ref == null){
                ref = createTemporal(tmp);
            }
            PrintValue(tmp.value, ref.pointer);
        }
        if (val instanceof Register reg){
            PrintValue(reg.value,reg.pointer);
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
                    //check if is a register, if so it might have the string value
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
