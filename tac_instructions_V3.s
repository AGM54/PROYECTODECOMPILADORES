.data
x: .word 0
#-----MAIN LOOP -----
.text
.globl main
main:
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
fibonacci:
	li $t0 , 1
	slt $t1 , $a0 , $t0
	beq $t1 , $zero , L_0
	L_0:
		move $v0 , $a0
		jr $ra
	L_1:
	addi $t1 , $a0 , -1
	addi $t0 , $a0 , -2
	addi $t2 , $a0 , -3
	li $v0 , null
	jr $ra
