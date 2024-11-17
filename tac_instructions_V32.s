.data
i: .word 2
_S0_ : .asciiz " es par"
_B_ : .space 200
_S1_ : .asciiz " es impar"
#-----MAIN LOOP -----
.text
.globl main
main:
la $s0 , i
li $t0 , 1
sw $t0 , 0($s0)
L_0:
	lw $t0 , i
	li $t1 , 5
	slt $t2 , $t0 , $t1
	beq $t2 , $zero , L_1
	lw $t2 , i
	li $t0 , 2
	div $t2, $t0
	mfhi $t1
	li $t2 , 0
	beq $t1 , $t2 , L_2
	bne $t1 , $t2 , L_4
	L_2:
		lw $t2 , i
		move $a0 , $t2
		jal concat_string_int
		la $a0 , _S0_
		jal concat_string_str
		la $a0 , _B_
		li $v0 , 4
		syscall
		j L_3
	L_4:
		lw $t2 , i
		move $a0 , $t2
		jal concat_string_int
		la $a0 , _S1_
		jal concat_string_str
		la $a0 , _B_
		li $v0 , 4
		syscall
	L_3:
	lw $t2 , i
	addi $t2, $t2, 1
	la $s0 , i
	sw $t2 , 0($s0)
	j L_0
L_1:
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
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
