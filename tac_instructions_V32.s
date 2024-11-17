.data
result: .word 1
m: .word 2
n: .word 3
#-----MAIN LOOP -----
.text
.globl main
main:
la $s0 , m
li $t3 , 2
sw $t3 , 0($s0)
la $s0 , n
li $t3 , 3
sw $t3 , 0($s0)
move $a0 , $a0
move $a1 , $a1
jal ackermann
la $s0 , result
sw $v0 , 0($s0)
lw $t3 , result
move $a0 , $t3
li $v0 , 1
syscall
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
ackermann:
	sub $sp, $sp, 12
	sw $a0 , 0($sp)
	sw $a1 , 4($sp)
	sw $ra , 8($sp)
	lw $t0 , m
	li $t1 , 0
	beq $t0 , $t1 , L_0
	bne $t0 , $t1 , L_2
	L_0:
		lw $t1 , n
		addi $t0, $t1, 1
		move $v0 , $t0
		lw $ra , 8($sp)
		add $sp, $sp, 12
		jr $ra
	L_2:
		lw $t1 , m
		li $t2 , 0
		slt $t3 , $t2 , $t1
		bne $t3 , $zero , L_6
		beq $t3 , $zero , L_5
		L_6:
		lw $t3 , n
		li $t2 , 0
		beq $t3 , $t2 , L_3
		bne $t3 , $t2 , L_5
		L_3:
			lw $t2 , m
			addi $t3 , $t2 , -1
			move $a0 , $t3
			li $a1 , 1
			jal ackermann
			lw $a0 , 0($sp)
			lw $ra , 8($sp)
			lw $a1 , 4($sp)
			move $v0 , $v0
			lw $ra , 8($sp)
			add $sp, $sp, 12
			jr $ra
		L_5:
			lw $t3 , m
			li $t2 , 0
			slt $t1 , $t2 , $t3
			bne $t1 , $zero , L_9
			beq $t1 , $zero , L_8
			L_9:
			lw $t1 , n
			li $t2 , 0
			slt $t3 , $t2 , $t1
			bne $t3 , $zero , L_7
			beq $t3 , $zero , L_8
			L_7:
				lw $t3 , m
				addi $t2 , $t3 , -1
				move $a0 , $t2
				move $a1 , $a1
				lw $t2 , n
				addi $t3 , $t2 , -1
				move $a2 , $t3
				jal ackermann
				lw $a0 , 0($sp)
				lw $ra , 8($sp)
				lw $a1 , 4($sp)
				move $a0 , $v0
				jal ackermann
				lw $a0 , 0($sp)
				lw $ra , 8($sp)
				lw $a1 , 4($sp)
				move $v0 , $v0
				lw $ra , 8($sp)
				add $sp, $sp, 12
				jr $ra
			L_8:
		L_4:
	L_1:
