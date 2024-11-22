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
    private List<String> temporalCalls = new ArrayList<>();  //functions/methods to be called
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
        if(register.startsWith(("$s"))){
            savePool.push(register);
        }
        registerDescription.put(register,null);
    }

    private Boolean addConcats = false;

    private String concatString = """
concat_string_str:
    # Find the end of the current buffer
    la $t0, _B_         # $t0 points to the buffer
find_end_str:
    lb $t1, 0($t0)        # Load byte from buffer
    beqz $t1, start_copy_str  # Break if null terminator is found
    addi $t0, $t0, 1      # Move to the next byte
    j find_end_str

start_copy_str:
    # Start copying the string from $a0 into the buffer
    move $t2, $a0         # $t2 points to the string to concatenate
copy_loop_str:
    lb $t3, 0($t2)        # Load byte from the source string
    beqz $t3, end_copy_str    # Break if null terminator is found
    sb $t3, 0($t0)        # Store the byte into the buffer
    addi $t2, $t2, 1      # Move to the next byte in the string
    addi $t0, $t0, 1      # Move to the next byte in the buffer
    j copy_loop_str

end_copy_str:
    # Add null terminator to the buffer
    sb $zero, 0($t0)      # Null terminator at the end of the buffer
    jr $ra                # Return to the caller
""";


    private String concatInteger = """
concat_string_int:
    # Step 1: Find the null terminator in the buffer
    la $t0 , _B_  # $t0 points to the buffer address
find_null_int:
    lb $t1, 0($t0)          # Load byte from the buffer
    beq $t1, $zero, convert_int # If null terminator is found, jump to convert
    addi $t0, $t0, 1        # Move to the next character
    j find_null_int

convert_int:
    # Step 2: Convert the integer in $a0 to its string representation
    move $t2, $a0           # Copy the integer to $t2 for conversion
    li $t3, 10              # $t3 = 10 (to get digits via modulo)
    addi $sp, $sp, -16      # Reserve stack space for the digits
    move $t4, $sp           # $t4 will store the digits in reverse order

convert_loop_int:
    beq $t2, $zero, append_int  # If integer is 0, jump to append
    div $t2, $t3            # Divide $t2 by 10
    mfhi $t5                # Get the remainder (last digit)
    addi $t5, $t5, 48       # Convert the digit to ASCII (add '0')
    sb $t5, 0($t4)          # Store the ASCII character on the stack
    addi $t4, $t4, -1       # Move to the next stack position
    mflo $t2                # Update $t2 with the quotient
    j convert_loop_int

append_int:
    addi $t4, $t4, 1        # Adjust $t4 to the first digit (stack pointer)
append_loop_int:
    lb $t5, 0($t4)          # Load the digit from the stack
    beq $t5, $zero, finalize_int # If null terminator, jump to finalize
    sb $t5, 0($t0)          # Store the digit in the buffer
    addi $t4, $t4, 1        # Move to the next digit
    addi $t0, $t0, 1        # Move to the next position in the buffer
    j append_loop_int

finalize_int:
    sb $zero, 0($t0)        # Append null terminator at the end of the string
    addi $sp, $sp, 16       # Restore the stack
    jr $ra                  # Return to caller
""";

    private String bufferCleaning = """
clear_buffer:
    # $a0 contains the buffer address (_B_)
    # $a1 contains the size of the buffer
    move $t0, $a0          # $t0 points to the start of the buffer
    move $t1, $a1          # $t1 is the size of the buffer (number of bytes to clear)

clear_loop:
    beqz $t1, clear_done   # If size is 0, we are done
    sb $zero, 0($t0)       # Set the current byte to 0
    addi $t0, $t0, 1       # Move to the next byte in the buffer
    addi $t1, $t1, -1      # Decrement the size counter
    j clear_loop           # Repeat the loop

clear_done:
    jr $ra                 # Return to the caller

            """;


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
            if(addConcats){
                writer.write(concatString);
                writer.write(concatInteger);
                writer.write(bufferCleaning);
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
    public void SwitchToTemporalInstructionsSet(){
        this.instructions = this.temporalCalls;
    }
    public void PushTemporalInstructions()
    {
        this.instructions.addAll(this.temporalCalls);
        this.temporalCalls.clear();
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
        if (type instanceof Class classic){
            return classic.size;
        }
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
            Register righty = createTemporal(right);
            loadInmediate(righty.pointer,right);
            releaseRegister(righty.pointer);
            right = righty;
        }
        if (left instanceof Number) {
            Register lefty = createTemporal(left);
            loadInmediate(lefty.pointer,left);
            releaseRegister(lefty.pointer);
            left = lefty;
        }
        instructions.add("\t".repeat(tabCounter) + "mul " + save.pointer + ", " +
                ((Register) left).pointer + ", " + ((Register) right).pointer);
    }
    public void div(Register save, Object left , Object right ){
        if (right instanceof Number) {
            Register righty = createTemporal(right);
            loadInmediate(righty.pointer,right);
            releaseRegister(righty.pointer);
            right = righty;
        }
        if (left instanceof Number) {
            Register lefty = createTemporal(left);
            loadInmediate(lefty.pointer,left);
            releaseRegister(lefty.pointer);
            left = lefty;
        }
        assert left instanceof Register;
        assert right instanceof Register;
        instructions.add("\t".repeat(tabCounter) + "div " +
                ((Register) left).pointer + ", " + ((Register) right).pointer);
        instructions.add("\t".repeat(tabCounter) + "mflo " + save.pointer); // Move quotient to save
    }
    public void mod(Register save, Object left , Object right ){
        if (right instanceof Number) {
            Register righty = createTemporal(right);
            loadInmediate(righty.pointer,right);
            releaseRegister(righty.pointer);
            right = righty;
        }
        if (left instanceof Number) {
            Register lefty = createTemporal(left);
            loadInmediate(lefty.pointer,left);
            releaseRegister(lefty.pointer);
            left = lefty;
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
        if(!jumpLabels.isEmpty()) {
            instructions.add("\t".repeat(tabCounter) + "beq " + left + " , " + right + " , " + jumpLabels.peek());
        }
        if(!jumpInverseLabels.isEmpty()){
            instructions.add("\t".repeat(tabCounter)  + "bne " +left + " , " + right + " , " + jumpInverseLabels.peek());
        }
    }
    public void notEquals(String left, String right){
        if(!jumpLabels.isEmpty()) {
            instructions.add("\t".repeat(tabCounter) + "bne " + left + " , " + right + " , " + jumpLabels.peek());
        }
        if(!jumpInverseLabels.isEmpty()){
            instructions.add("\t".repeat(tabCounter)  + "beq " +left + " , " + right + " , " + jumpInverseLabels.peek());
        }
    }

    public Register setReturnRegister(Object val){
        setRegister("$v0",val);
        return  new Register("$v0",val);
    }

    //stack methods
    public int reserveOnStack(int size){
        // Ensure the size is a multiple of 4
        int alignedSize = (size + 3) & ~3; // Round up to the nearest multiple of 4

        instructions.add("\t".repeat(tabCounter) + "sub $sp, $sp, " + alignedSize);
        StackPointer += size;
        return alignedSize;
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
                loadWord(ref.pointer,tmp.name);
            }
            PrintValue(tmp.value, ref.pointer);
            return;
        }
        if (val instanceof Register reg){
            releaseRegister(reg.pointer);
            PrintValue(reg.value,reg.pointer);
            return;
        }
        int mode = -1;
        if (val instanceof Integer){
            mode = 1;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $a0 , "+ refPoint);
                releaseRegister(refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $a0 , "+ val);
            }
        } else if (val instanceof Double) {
            mode = 2;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $f12 , "+ refPoint);
                releaseRegister(refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $f12 , "+ val);
            }
        } else if (val instanceof  Float) {
            mode = 3;
            if (!refPoint.isBlank()){
                instructions.add("\t".repeat(tabCounter) + "move $f12 , "+ refPoint);
                releaseRegister(refPoint);
            }else{
                instructions.add("\t".repeat(tabCounter) + "li $f12 , "+ val);
            }
        } else if( val instanceof String){
            if(String.valueOf(val).startsWith("$t")){

            }else{
                mode = 4;
                if (!refPoint.isBlank()){     //check if is a register, if so it might have the string value  , so release it
                    releaseRegister(refPoint);
                }

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
        instructions.add("\t".repeat(tabCounter) + "li $v0 , "+ mode);
        instructions.add("\t".repeat(tabCounter) + "syscall");

    }

    public void concatString (Object str){
        addConcats = true;
        if(!hasStringBuffer){ //no string buffer yet? create it
            hasStringBuffer = true;
            dataHeader.add("_B_ : .space 200");
        }
        //check that the requiered registers are free, otherwise save into pointer
        int offset = 0;
        String checkList[] = {"$t0", "$t1","$t2","$t3","$a0" };
        HashMap<String,String> recoverAddres = new HashMap<>();
        for (String register: checkList){
            if(registerDescription.getOrDefault(register,null) != null){
                recoverAddres.put(register,offset+"($sp)");
                offset += calculateSize(registerDescription.get(register));
            }
        }
        if(offset > 0){
            offset = reserveOnStack(offset);
        }
        for(Map.Entry<String,String> rec : recoverAddres.entrySet()){
            saveWordInto(rec.getValue(),rec.getKey());
        }
        if(str instanceof Register r){
            if(r.pointer.endsWith("($a0)")){ //so it was an instance thing
                Register tmp = createTemporal(r.value);
                loadAddres(tmp.pointer,r.pointer);
                loadWord("$a0", "0(" + tmp.pointer +")");
                releaseRegister(r.pointer);
        }
        }else{
            assert  str instanceof String;
            String label = getConstantLabel((String) str);
            loadAddres("$a0", label.isBlank()? "_B_" : label);
        }
        jumpAndLink("concat_string_str");
        for(Map.Entry<String,String> rec : recoverAddres.entrySet()){
            loadWord(rec.getKey(),rec.getValue());
        }
        if(offset > 0){
            releaseOnStack(offset);
        }
    }
    public void concatInteger (Object val){
        addConcats = true;
        if(!hasStringBuffer){ //no string buffer yet? create it
            hasStringBuffer = true;
            dataHeader.add("_B_ : .space 200");
        }
        int offset = 0;

        //check that the requiered registers are free, otherwise save into pointer
        String checkList[] = {"$t0", "$t1","$t2","$t3","$t4","$t5","$a0" };
        HashMap<String,String> recoverAddres = new HashMap<>();
        for (String register: checkList){
            if(registerDescription.getOrDefault(register,null) != null){
                recoverAddres.put(register,offset+"($sp)");
                offset += calculateSize(registerDescription.get(register));
            }
        }
        if(offset > 0){
            offset = reserveOnStack(offset);
        }
        for(Map.Entry<String,String> rec : recoverAddres.entrySet()){
            loadWord(rec.getKey(),rec.getValue());
            saveWordInto(rec.getValue(),rec.getKey());
        }


        if (val instanceof Register v){
            if(v.pointer.endsWith("($a0)")){ //so it was an instance thing
                Register tmp = createTemporal(v.value);
                loadWord(tmp.pointer,v.pointer);
                loadWord("$a0","0(" + tmp.pointer +")");
                releaseRegister(v.pointer);
            }else{
                moveInto("$a0",v.pointer);
                releaseRegister(v.pointer);
            }
        }else{
            Register tmp = createTemporal(val);
            loadInmediate(tmp.pointer,val);
            moveInto("$a0",tmp.pointer);
            releaseRegister(tmp.pointer);
        }
        jumpAndLink("concat_string_int");
        for(Map.Entry<String,String> rec : recoverAddres.entrySet()){
            loadWord(rec.getKey(),rec.getValue());
        }
        if(offset > 0){
            releaseOnStack(offset);
        }
    }
}
