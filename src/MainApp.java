import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

public class MainApp extends Application {

    private TextArea inputArea;
    private TextArea outputArea;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {

        primaryStage.setTitle("CompiScript Analyzer");

        inputArea = new TextArea();
        inputArea.setPromptText("Enter your CompiScript code here...");
        inputArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px;");

        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setStyle("-fx-font-family: 'Consolas'; -fx-font-size: 14px; -fx-control-inner-background: #f0f0f0; -fx-text-fill: #333333;");

        Button analyzeButton = new Button("Analyze");
        analyzeButton.setStyle("-fx-background-color: #0077cc; -fx-text-fill: white; -fx-font-size: 14px;");
        analyzeButton.setOnAction(e -> analyzeCode());

        VBox vbox = new VBox(10, inputArea, analyzeButton, outputArea);
        vbox.setStyle("-fx-padding: 10; -fx-background-color: #ffffff; -fx-border-color: #cccccc; -fx-border-radius: 8px; -fx-background-radius: 8px;");
        Scene scene = new Scene(vbox, 600, 400);

        primaryStage.setScene(scene);
        primaryStage.show();
    }

    private void analyzeCode() {
        String code = inputArea.getText();

        // Redirigir la salida estándar (System.out) a un ByteArrayOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(baos);
        PrintStream old = System.out;
        System.setOut(ps);

        try {
            CharStream input = CharStreams.fromString(code);
            CompiScriptLexer lexer = new CompiScriptLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            CompiScriptParser parser = new CompiScriptParser(tokens);
            ParseTree tree = parser.program();

            CompiScriptCustomVisitor visitor = new CompiScriptCustomVisitor();
            visitor.visit(tree);
            visitor.printSymbols();

        } catch (Exception e) {
            outputArea.setText("Error: " + e.getMessage());
        } finally {
            // Restaurar la salida estándar y mostrar lo capturado en la interfaz
            System.out.flush();
            System.setOut(old);


            String output = baos.toString();
            System.out.print(output);

            outputArea.setText(output);
        }
    }
}
