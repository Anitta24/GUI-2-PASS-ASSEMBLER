import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.Scanner;

public class PassOneTwoAssembler extends JFrame implements ActionListener {
    private JTextArea assemblyArea, optabArea, intermediateArea, symtabArea, objectCodeArea, objectprogramArea;
    private JButton passOneButton, passTwoButton;

    private String intermediateCode = "";
    private HashMap<String, String> symtab = new HashMap<>();
    private HashMap<String, String> optab = new HashMap<>();

    private String programName = "";
    private int programStartAddress = 0;
    private int programLength = 0;

    public PassOneTwoAssembler() {

        setTitle("Pass One and Pass Two Assembler");
        setSize(1000, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        assemblyArea = new JTextArea(10, 40);
        assemblyArea.setBorder(BorderFactory.createTitledBorder("Input Assembly Code"));

        optabArea = new JTextArea(10, 40);
        optabArea.setBorder(BorderFactory.createTitledBorder("OPTAB"));

        intermediateArea = new JTextArea(10, 40);
        intermediateArea.setEditable(false);
        intermediateArea.setBorder(BorderFactory.createTitledBorder("Intermediate Code"));

        symtabArea = new JTextArea(10, 40);
        symtabArea.setEditable(false);
        symtabArea.setBorder(BorderFactory.createTitledBorder("Symbol Table"));

        objectCodeArea = new JTextArea(10, 40);
        objectCodeArea.setEditable(false);
        objectCodeArea.setBorder(BorderFactory.createTitledBorder("Object Code"));

        objectprogramArea = new JTextArea(10, 40);
        objectprogramArea.setEditable(false);
        objectprogramArea.setBorder(BorderFactory.createTitledBorder("Object Program"));

        passOneButton = new JButton("Process Pass One");
        passOneButton.addActionListener(this);

        passTwoButton = new JButton("Process Pass Two");
        passTwoButton.addActionListener(this);
        passTwoButton.setEnabled(false);

        JPanel inputPanel = new JPanel(new GridLayout(1, 2, 10, 10));
        inputPanel.add(new JScrollPane(assemblyArea));
        inputPanel.add(new JScrollPane(optabArea));

        JPanel outputPanel = new JPanel(new GridLayout(4, 1, 10, 10));
        outputPanel.add(new JScrollPane(intermediateArea));
        outputPanel.add(new JScrollPane(symtabArea));
        outputPanel.add(new JScrollPane(objectCodeArea));
        outputPanel.add(new JScrollPane(objectprogramArea));

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(passOneButton);
        buttonPanel.add(passTwoButton);

        add(inputPanel, BorderLayout.NORTH);
        add(outputPanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == passOneButton) {
            processPassOne();
        } else if (e.getSource() == passTwoButton) {
            processPassTwo();
        }
    }

    private void processPassOne() {
        String assemblyCode = assemblyArea.getText().trim();
        String optabInput = optabArea.getText().trim();

        if (assemblyCode.isEmpty() || optabInput.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please provide both Assembly Code and OPTAB.");
            return;
        }

        // Parse OPTAB
        parseOptab(optabInput);

        Scanner scanner = new Scanner(assemblyCode);
        StringBuilder intermediateBuilder = new StringBuilder();
        symtab.clear();
        intermediateCode = "";

        int locctr = 0, start = 0;
        boolean started = false;

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            String[] tokens = line.split("\\s+");

            if (tokens.length == 0) continue;

            String label = tokens[0];
            String opcode = tokens.length > 1 ? tokens[1] : "";
            String operand = tokens.length > 2 ? tokens[2] : "";

            if (opcode.equals("START")) {
                programName = label;
                start = Integer.parseInt(operand, 16);
                locctr = start;
                programStartAddress = locctr;
                intermediateBuilder.append(String.format("%04X", locctr)).append("\t").append(label).append("\t").append(opcode).append("\t").append(operand).append("\n");
                started = true;
                continue;
            } else if (!started) {
                locctr = 0;
                started = true;
            }

            if (!opcode.equals("END")) {
                intermediateBuilder.append(String.format("%04X", locctr)).append("\t").append(label).append("\t").append(opcode).append("\t").append(operand).append("\n");

                if (!label.equals("") && !label.isEmpty()) {
                    symtab.put(label, Integer.toHexString(locctr));
                }

                if (optab.containsKey(opcode)) {
                    locctr += 3;
                } else {
                    switch (opcode) {
                        case "WORD": locctr += 3; break;
                        case "RESW": locctr += 3 * Integer.parseInt(operand); break;
                        case "BYTE": locctr += operand.length() - 3; break; // For handling constants like BYTE C'EOF'
                        case "RESB": locctr += Integer.parseInt(operand); break;
                    }
                }
            } else {
                programLength = locctr - start;
                intermediateBuilder.append(String.format("%04X", locctr)).append("\t").append(label).append("\t").append(opcode).append("\t").append(operand).append("\n");
                break;
            }
        }

        intermediateCode = intermediateBuilder.toString();
        intermediateArea.setText(intermediateCode);

        StringBuilder symtabBuilder = new StringBuilder();
        symtab.forEach((key, value) -> symtabBuilder.append(key).append("\t").append(value).append("\n"));
        symtabArea.setText(symtabBuilder.toString());

        passTwoButton.setEnabled(true);
    }

    private void processPassTwo() {
        Scanner scanner = new Scanner(intermediateCode);
        StringBuilder programBuilder = new StringBuilder();
        StringBuilder textRecord = new StringBuilder();
        StringBuilder objectBuilder = new StringBuilder();
        int textStartAddress = 0;
        int textLength = 0;

        // Header record
        programBuilder.append("H").append("^")
                .append(programName).append("^")
                .append(String.format("%06X", programStartAddress)).append("^")
                .append(String.format("%06X", programLength)).append("\n");

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            String[] tokens = line.split("\\s+");
            if (tokens.length < 4) continue;

            String address = tokens[0];
            String opcode = tokens[2];
            String operand = tokens[3];

            if (optab.containsKey(opcode)) {
                String code = optab.get(opcode);
                String operandAddress = symtab.getOrDefault(operand, "0000");
                String objectCode = code + operandAddress;

                // New text record if current one is too long
                if (textLength + objectCode.length() > 60) {
                    programBuilder.append("T").append("^")
                            .append(String.format("%06X", textStartAddress)).append("^")
                            .append(String.format("%02X", textLength / 2)).append("^")
                            .append(textRecord).append("\n");

                    textRecord = new StringBuilder();
                    textStartAddress = Integer.parseInt(address, 16);
                    textLength = 0;
                }

                if (textLength == 0) {
                    textStartAddress = Integer.parseInt(address, 16);
                }

                textRecord.append(objectCode).append("^");
                textLength += objectCode.length();
                objectBuilder.append(address).append("\t").append(objectCode).append("\n");
            }
        }

        // Write the last text record
        if (textLength > 0) {
            programBuilder.append("T").append("^")
                    .append(String.format("%06X", textStartAddress)).append("^")
                    .append(String.format("%02X", textLength / 2)).append("^")
                    .append(textRecord).append("\n");
        }

        // End record
        programBuilder.append("E").append("^")
                .append(String.format("%06X", programStartAddress)).append("\n");

        objectCodeArea.setText(objectBuilder.toString());
        objectprogramArea.setText(programBuilder.toString());
    }

    private void parseOptab(String optabInput) {
        Scanner scanner = new Scanner(optabInput);
        optab.clear();

        while (scanner.hasNextLine()) {
            String line = scanner.nextLine().trim();
            String[] parts = line.split("\\s+");
            if (parts.length >= 2) {
                optab.put(parts[0], parts[1]);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            PassOneTwoAssembler frame = new PassOneTwoAssembler();
            frame.setVisible(true);
        });
    }
}
