import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;

public class MainGUIWithSwingTextInput {

    private JTextArea inputArea;
    private JTextArea outputArea;

    public static void main(String[] args) {
        // Establecer estilo visual
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        JFrame frame = new JFrame("CompiScript Parser");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);  // Ventana más grande

        MainGUIWithSwingTextInput app = new MainGUIWithSwingTextInput();
        app.createUI(frame);

        frame.setVisible(true);
    }

    private void createUI(JFrame frame) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(new Color(30, 30, 30));  // Fondo oscuro elegante
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(10, 10, 10, 10);

        // Áreas de entrada con estilo
        inputArea = new JTextArea("Escribe o pega tu código aquí...");
        inputArea.setLineWrap(true);
        inputArea.setWrapStyleWord(true);
        inputArea.setFont(new Font("Consolas", Font.PLAIN, 16));
        inputArea.setBackground(new Color(40, 40, 40));  // Fondo gris oscuro para el input
        inputArea.setForeground(new Color(200, 200, 200));  // Texto claro
        inputArea.setCaretColor(Color.WHITE);
        JScrollPane inputScrollPane = new JScrollPane(inputArea);

        // Botón procesar con fondo rosado y fuente negra
        JButton processButton = new JButton("Procesar");
        processButton.setPreferredSize(new Dimension(120, 40));
        processButton.setBackground(new Color(255, 105, 180)); // Rosado
        processButton.setForeground(Color.BLACK); // Texto negro
        processButton.setFont(new Font("Arial", Font.BOLD, 16));
        processButton.setFocusPainted(false);
        processButton.setCursor(new Cursor(Cursor.HAND_CURSOR)); // Cambiar cursor
        processButton.setBorder(BorderFactory.createEmptyBorder()); // Sin bordes
        processButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String userInput = inputArea.getText();
                processText(userInput);
            }
        });

        // Área de salida con estilo
        outputArea = new JTextArea();
        outputArea.setEditable(false);
        outputArea.setLineWrap(true);
        outputArea.setWrapStyleWord(true);
        outputArea.setFont(new Font("Consolas", Font.PLAIN, 16));
        outputArea.setBackground(new Color(40, 40, 40));  // Fondo gris oscuro
        outputArea.setForeground(new Color(200, 200, 200));  // Texto claro
        outputArea.setCaretColor(Color.WHITE);
        JScrollPane outputScrollPane = new JScrollPane(outputArea);

        // Layout del panel
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1;
        gbc.weighty = 0.5;
        panel.add(inputScrollPane, gbc);

        gbc.gridy = 1;
        gbc.weighty = 0.1;
        gbc.anchor = GridBagConstraints.CENTER;
        panel.add(processButton, gbc);

        gbc.gridy = 2;
        gbc.weighty = 1.0;
        panel.add(outputScrollPane, gbc);

        frame.getContentPane().add(panel);
    }

    private void processText(String text) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);

        try {
            CharStream input = CharStreams.fromString(text);
            CompiScriptLexer lexer = new CompiScriptLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CompiScriptParser parser = new CompiScriptParser(tokens);
            ParseTree tree = parser.program();
            CompiScriptCustomVisitor visitor = new CompiScriptCustomVisitor();
            visitor.visit(tree);

            outputArea.setText("");  // Limpiar salida anterior

            appendToOutput("Árbol Sintáctico:\n" + tree.toStringTree(parser));

            appendToOutput("\n\nSímbolos:\n");
            visitor.printSymbols();

            IntermediateCodeVisitor icVisitor = new IntermediateCodeVisitor(visitor.getFusedSymbolTable());
            icVisitor.visit(tree);

            appendToOutput("\n\nInstrucciones TAC:\n");
            List<String> tacInstructions = icVisitor.getInstructions();
            for (String instruction : tacInstructions) {
                appendToOutput(instruction);
            }
        } catch (Exception e) {
            appendToOutput("Error durante el análisis: " + e.getMessage());
        } finally {
            System.out.flush();
            System.setOut(old);

            appendToOutput(baos.toString());
        }
    }

    private void appendToOutput(String text) {
        outputArea.append(text + "\n");
    }
}
