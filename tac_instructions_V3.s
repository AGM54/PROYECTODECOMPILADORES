.data
x: .word 12
y: .word 2
_S_0: .asciiz "valor de x"
#-----MAIN LOOP -----
.text
.globl main
main:
la $s1, y
li $t0, 2
sw $t0, 0($s1)
li $a0 , 1
li $a1 , 2
li $a2 , 3
li $a3 , 4
sub $sp, $sp, 4
lw $t0 , y
sw $t0 , 0($sp)
jal argstest
add $sp, $sp, 4
la $s1, x
move $t0, $v0
sw $t0, 0($s1)
la $a0 , _S_0
li $v0 , 4
syscall
move $a0 , $t0
li $v0 , 1
syscall
li $v0, 10
syscall
#-----MAIN TERMINATION-----
#-----FUNCTIONS-----
argstest:
	add $t0, $a0, $a1
	add $t0, $t0, $a2
	add $t0, $t0, $a3
	lw $t1 , 0($sp)
	add $t0, $t0, $t1
	move $v0 $t0
	jr $ra
