package com.martian.tools.imagecompression;

import com.google.common.base.Function;
import com.google.common.base.Strings;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.martian.tools.imagecompression.Core.Message;
import com.martian.tools.imagecompression.Core.Options;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DropTarget;
import java.awt.dnd.DropTargetAdapter;
import java.awt.dnd.DropTargetDropEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.util.HashSet;
import java.util.List;

/**
 * @author Shenluw
 *         创建日期：2016/12/2 16:36
 */
public class View implements Message {
    private JTextField keyField;
    private JTextArea infoText;
    private JButton chooseButton;
    private JButton saveToButton;
    private JButton resetButton;
    private JButton setKeyButton;
    public JPanel root;
    private JTextField savePathTextField;
    private JButton saveButton;


    Options options = new Options();

    public View() {
        setKeyButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String text = keyField.getText();
                if (!Strings.isNullOrEmpty(text)) {
                    options.key = text;
                }
            }
        });

        chooseButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
                chooser.setMultiSelectionEnabled(true);
                chooser.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e1) {
                        File[] files = chooser.getSelectedFiles();
                        if (files != null) {
                            for (File file : files) {
                                if (options.filter.accept(file)) {
                                    options.froms.add(file.getAbsolutePath());
                                    infoText.append(String.format("add :%s", file.getAbsolutePath()));
                                    infoText.append("\n");
                                }
                            }
                        }
                    }
                });
                chooser.showOpenDialog(root);
            }
        });
        saveToButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                JFileChooser chooser = new JFileChooser();
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                chooser.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e1) {
                        File file = chooser.getSelectedFile();
                        if (file != null && file.isDirectory()) {
                            options.to = file.getAbsolutePath();
                            options.errorLog = file.getAbsolutePath() + File.separator + "error.txt";
                            new Thread(new Runnable() {
                                @Override
                                public void run() {
                                    new Core(View.this).compression(options);
                                }
                            }).start();
                        }
                    }
                });
                chooser.showOpenDialog(root);
            }
        });

        saveButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                String text = savePathTextField.getText();
                if (Strings.isNullOrEmpty(text)) {
                    JOptionPane.showInternalMessageDialog(root, "save path is empty", "error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                File input = new File(text);
                if (Files.isFile().apply(input)) {
                    JOptionPane.showInternalMessageDialog(root, "save path is file", "error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (!input.exists()) {
                    if (!input.mkdirs()) {
                        JOptionPane.showInternalMessageDialog(root, "mkdir error", "error", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                }

                options.to = input.getAbsolutePath();
                options.errorLog = input.getAbsolutePath() + File.separator + "error.txt";
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        new Core(View.this).compression(options);
                    }
                }).start();
            }
        });

        resetButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                super.mouseClicked(e);
                options = new Options();
                options.key = key;
                infoText.setText("");
            }
        });

        options.key = key;

        drop(infoText, new Function<File, Void>() {
            @Override
            public Void apply(File input) {
                if (options.filter.accept(input)) {
                    options.froms.add(input.getAbsolutePath());
                    infoText.append(String.format("add : %s", input.getAbsolutePath()));
                    infoText.append("\n");
                    infoText.append(String.format("file count : %d", Core.extractDir(input, options.filter, new HashSet<>()).size()));
                    infoText.append("\n");
                }
                return null;
            }
        });

        drop(savePathTextField, new Function<File, Void>() {
            @Override
            public Void apply(File input) {
                savePathTextField.setText(input.getAbsolutePath());
                return null;
            }
        });

    }

    public void drop(Component component, Function<File, Void> function) {
        new DropTarget(component, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    Transferable e = dtde.getTransferable();
                    if (e.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                        dtde.acceptDrop(3);
                        List lt = (List) e.getTransferData(DataFlavor.javaFileListFlavor);
                        for (Object o : lt) {
                            if (o instanceof File) {
                                if (((File) o).exists())
                                    function.apply((File) o);
                            }
                        }
                        dtde.dropComplete(true);
                    } else {
                        dtde.rejectDrop();
                    }
                } catch (Exception e) {
                    JOptionPane.showInternalMessageDialog(root, "drop error: " + e.getMessage(), "error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    }

    static String key;

    public static void main(String[] args) throws IOException, URISyntaxException {
        String key = Resources.toString(Resources.getResource("keys.txt"), Charset.forName("utf-8"));
        JFrame frame = new JFrame("compression");
        frame.setLocation(200, 150);
        System.out.println(key);
        View.key = key;
        View view = new View();
        frame.setContentPane(view.root);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
        frame.setSize(500, 400);
    }

    @Override
    public void send(String msg, Object obj) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                infoText.append(msg);
                infoText.append("\n");
            }
        });
    }
}
