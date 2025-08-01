/* This file is part of KeY - https://key-project.org
 * KeY is licensed under the GNU General Public License Version 2
 * SPDX-License-Identifier: GPL-2.0-only */
package de.uka.ilkd.key.gui.smt;

import java.awt.*;
import javax.swing.*;
import javax.swing.event.TableModelEvent;
import javax.swing.plaf.basic.BasicProgressBarUI;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import de.uka.ilkd.key.gui.IssueDialog;
import de.uka.ilkd.key.gui.MainWindow;
import de.uka.ilkd.key.gui.smt.ProgressModel.ProcessColumn.ProcessData;
import de.uka.ilkd.key.gui.smt.ProgressTable.ProgressTableListener;
import de.uka.ilkd.key.smt.SMTFocusResults;

import org.key_project.util.java.SwingUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog showing launched SMT processes and results.
 */
public class ProgressDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    private static final Logger LOGGER = LoggerFactory.getLogger(ProgressDialog.class);

    private final ProgressTable table;
    /**
     * Button to apply the results of running the SMT solver.
     * May close some open goals if the solver returned unsat.
     */
    private JButton applyButton;
    /**
     * Button to evaluate the unsat core provided by the SMT solver.
     *
     * @see SMTFocusResults
     */
    private JButton focusButton;
    /**
     * Button to stop the launched SMT solvers.
     */
    private JButton stopButton;
    /**
     * Scroll pane listing the open goals and the results of running each SMT solver on them.
     */
    private JScrollPane scrollPane;
    /**
     * Overall progress of the SMT solvers (# goals started / total goals).
     */
    private JProgressBar progressBar;
    private final ProgressDialogListener listener;

    /**
     * Current state of the dialog.
     */
    public enum Modus {
        /**
         * SMT solvers are running and may be stopped by the user.
         */
        SOLVERS_RUNNING,
        /**
         * SMT solvers are done (or terminated). Results may be applied by the user.
         */
        SOLVERS_DONE
    }

    /**
     * Current state of the dialog.
     */
    private Modus modus = Modus.SOLVERS_RUNNING;

    public interface ProgressDialogListener extends ProgressTableListener {
        void applyButtonClicked();

        void stopButtonClicked();

        void discardButtonClicked();

        void additionalInformationChosen(Object obj);

        void focusButtonClicked();
    }

    public ProgressDialog(ProgressModel model, ProgressDialogListener listener,
            boolean counterexample, int resolution, int progressBarMax, String[] labelTitles,
            String... titles) {
        super(MainWindow.getInstance());
        table = new ProgressTable(resolution, listener, labelTitles);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.getTableHeader().setReorderingAllowed(false);
        table.setModel(model, titles);
        this.listener = listener;
        if (counterexample) {
            this.setTitle("SMT Counterexample Search");
        } else {
            this.setTitle("SMT Interface");
        }

        getProgressBar().setMaximum(progressBarMax);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setModal(true);
        Container contentPane = this.getContentPane();
        contentPane.setLayout(new GridBagLayout());
        Box buttonBox = Box.createHorizontalBox();
        buttonBox.add(Box.createHorizontalGlue());
        buttonBox.add(getStopButton());
        buttonBox.add(Box.createHorizontalStrut(5));
        if (!counterexample) {
            buttonBox.add(getFocusButton());
            buttonBox.add(Box.createHorizontalStrut(5));
            buttonBox.add(getApplyButton());
            buttonBox.add(Box.createHorizontalStrut(5));
        }


        GridBagConstraints constraints = new GridBagConstraints(0, 0, 1, 1, 1.0, 0.0,
            GridBagConstraints.CENTER, GridBagConstraints.BOTH, new Insets(5, 5, 0, 5), 0, 0);

        contentPane.add(getProgressBar(), constraints);
        constraints.gridy++;
        constraints.weighty = 2.0;
        contentPane.add(getScrollPane(), constraints);
        constraints.gridy += 2;
        constraints.weighty = 0.0;
        constraints.insets.bottom = 5;
        contentPane.add(buttonBox, constraints);
        this.pack();
        // always set the location last, otherwise it is not centered!
        setLocationRelativeTo(MainWindow.getInstance());
    }

    public void setProgress(int value) {
        getProgressBar().setValue(value);
    }

    public JProgressBar getProgressBar() {
        if (progressBar == null) {
            progressBar = new JProgressBar();

        }

        return progressBar;
    }

    private JButton getFocusButton() {
        if (focusButton == null) {
            focusButton = new JButton("Focus goals");
            focusButton.setToolTipText("Focus open goals to the formulas required to close them"
                + " (as specified by the SMT solver's unsat core)");
            focusButton.setEnabled(false);
            focusButton.addActionListener(e -> {
                try {
                    listener.focusButtonClicked();
                } catch (Exception exception) {
                    LOGGER.error("", exception);
                    // There may be exceptions during rule application that should not be lost.
                    IssueDialog.showExceptionDialog(ProgressDialog.this, exception);
                }
            });
        }
        return focusButton;
    }

    private JButton getApplyButton() {
        if (applyButton == null) {
            applyButton = new JButton("Apply");
            applyButton.setToolTipText(
                "Apply the results (i.e. close goals if the SMT solver was successful)");
            applyButton.setEnabled(false);
            applyButton.addActionListener(e -> {
                try {
                    listener.applyButtonClicked();
                } catch (Exception exception) {
                    // There may be exceptions during rule application that should not be lost.
                    LOGGER.error("", exception);
                    IssueDialog.showExceptionDialog(ProgressDialog.this, exception);
                }
            });
        }
        return applyButton;
    }

    private JScrollPane getScrollPane() {
        if (scrollPane == null) {
            scrollPane = SwingUtil.createScrollPane(table);
        }
        return scrollPane;
    }

    private JButton getStopButton() {
        if (stopButton == null) {
            stopButton = new JButton("Stop");
            stopButton.addActionListener(e -> {
                if (modus.equals(Modus.SOLVERS_DONE)) {
                    listener.discardButtonClicked();
                }
                if (modus.equals(Modus.SOLVERS_RUNNING)) {
                    listener.stopButtonClicked();
                }
            });
        }
        return stopButton;
    }

    public void setModus(Modus m) {
        modus = m;
        switch (modus) {
            case SOLVERS_DONE -> {
                stopButton.setText("Discard");
                if (applyButton != null) {
                    applyButton.setEnabled(true);
                }
                if (focusButton != null) {
                    focusButton.setEnabled(true);
                }
            }
            case SOLVERS_RUNNING -> {
                stopButton.setText("Stop");
                if (applyButton != null) {
                    applyButton.setEnabled(false);
                }
            }
        }
    }



    public static void main(String[] args) throws InterruptedException {
        final ProgressModel model = new ProgressModel();
        model.addColumn(new ProgressModel.TitleColumn("Summary", "1", "2", "3", "4"));
        model.addColumn(new ProgressModel.ProcessColumn(4));
        model.addColumn(new ProgressModel.ProcessColumn(4));
        model.addColumn(new ProgressModel.ProcessColumn(4));
        String[] infoLabels =
            { "Processed", "Closed: ", "Unknown: ", "Counter Example:", "Errors:" };

        ProgressDialog dialog = new ProgressDialog(model, null, true, 100, 10, infoLabels, "", "Z3",
            "Simplify", "Yices");
        dialog.setVisible(true);
        dialog.setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        for (int i = 0; i < 1000; i++) {
            final int p = i;
            SwingUtilities.invokeLater(() -> model.setProgress(p / 10, 1, 2));
            Thread.sleep(10);
        }
        model.setText("TIMEOUT", 1, 2);
        model.setEditable(true);

    }
}


class ProgressTable extends JTable {

    private static final long serialVersionUID = 1L;
    private static final int NUMBER_OF_VISIBLE_ROWS = 8;

    public interface ProgressTableListener {
        void infoButtonClicked(int column, int row);
    }


    public static class ProgressPanel extends JPanel {
        private static final long serialVersionUID = 1L;
        private JProgressBar progressBar;
        private JButton infoButton;

        private JProgressBar getProgressBar() {
            if (progressBar == null) {
                progressBar = new JProgressBar();
                int height = getInfoButton().getMaximumSize().height;
                progressBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
                progressBar.setString("Test");
                progressBar.setStringPainted(true);
                progressBar.setFont(this.getFont());
            }
            return progressBar;
        }

        private JButton getInfoButton() {
            if (infoButton == null) {
                infoButton = new JButton("Info");
                infoButton.setFont(this.getFont());

                Dimension dim = new Dimension();
                infoButton.setMargin(new Insets(0, 0, 0, 0));

                dim.height = this.getFontMetrics(this.getFont()).getHeight() + 2;
                dim.width = dim.height * 3;

                infoButton.setMinimumSize(dim);
                infoButton.setPreferredSize(dim);
                infoButton.setMaximumSize(dim);

            }
            return infoButton;
        }

        ProgressPanel() {

            this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.add(Box.createVerticalStrut(2));
            Box content = Box.createHorizontalBox();
            content.add(Box.createHorizontalStrut(2));
            content.add(getProgressBar());
            content.add(Box.createHorizontalStrut(2));
            content.add(getInfoButton());
            content.add(Box.createHorizontalStrut(2));
            this.add(content);
            this.add(Box.createVerticalStrut(2));
        }

        public void setValue(int value) {
            getProgressBar().setValue(value);
        }

        public void setText(String text) {
            getProgressBar().setString(text);
            getProgressBar().setStringPainted(text != null && !text.isEmpty());
        }
    }



    private final ProgressPanel progressPanelRenderer = new ProgressPanel();
    private ProgressPanel progressPanelEditor;



    private class ProgressCellEditor extends AbstractCellEditor implements TableCellEditor {

        private static final long serialVersionUID = 1L;



        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected,
                int row, int column) {

            currentEditorCell.x = column;
            currentEditorCell.y = row;
            ProcessData data = (ProcessData) value;
            prepareProgressPanel(getProgressPanelEditor(), data);
            return getProgressPanelEditor();
        }



        @Override
        public Object getCellEditorValue() {
            return null;
        }

    }



    private void prepareProgressPanel(ProgressPanel panel, final ProcessData data) {
        panel.setValue(data.getProgress());
        panel.setText(data.getText());
        panel.infoButton.setEnabled(data.isEditable());
        panel.progressBar.setBackground(data.getBackgroundColor());
        panel.progressBar.setForeground(data.getForegroundColor());
        panel.progressBar.setUI(new BasicProgressBarUI() {


            @Override
            protected Color getSelectionForeground() {
                return data.getSelectedTextColor();
            }

            protected Color getSelectionBackground() { return data.getTextColor(); }
        });

    }

    private final TableCellRenderer renderer =
        (table, value, isSelected, hasFocus, row, column) -> {
            ProcessData data = (ProcessData) value;
            prepareProgressPanel(progressPanelRenderer, data);
            return progressPanelRenderer;
        };


    private final TableCellEditor editor = new ProgressCellEditor();
    private final Point currentEditorCell = new Point();



    public ProgressTable(int resolution, ProgressTableListener listener, String... titles) {
        this.setDefaultRenderer(ProgressModel.ProcessColumn.class, renderer);
        this.setDefaultEditor(ProgressModel.ProcessColumn.class, editor);
        init(getProgressPanelEditor(), this.getFont(), resolution, listener);
        init(progressPanelRenderer, this.getFont(), resolution, listener);

    }

    private void init(ProgressPanel panel, Font font, int resolution,
            final ProgressTableListener listener) {
        panel.setFont(font);
        panel.progressBar.setMaximum(resolution);
        panel.infoButton.addActionListener(
            e -> listener.infoButtonClicked(currentEditorCell.x - 1, currentEditorCell.y));


    }


    public void setModel(ProgressModel model, String... titles) {

        assert titles.length == model.getColumnCount();
        super.setModel(model);
        for (int i = 0; i < titles.length; i++) {
            TableColumn col = getTableHeader().getColumnModel().getColumn(i);

            col.setHeaderValue(titles[i]);
            packColumn(this, i, 5);

        }
        for (int i = 0; i < model.getRowCount(); i++) {
            this.setRowHeight(progressPanelRenderer.getPreferredSize().height + 5);
        }



    }

    // @Override
    // public Dimension getPreferredSize() {
    // Dimension dim = new Dimension(super.getPreferredSize());
    // dim.height = Math.min(NUMBER_OF_VISIBLE_ROWS *
    // (progressPanelRenderer.getPreferredSize().height+5), dim.height);
    // return dim;
    // }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        Dimension dim = new Dimension(super.getPreferredScrollableViewportSize());

        dim.height =
            Math.min(NUMBER_OF_VISIBLE_ROWS * (progressPanelRenderer.getPreferredSize().height + 5),
                dim.height);
        return dim;
    }

    public static void packColumn(JTable table, int vColIndex, int margin) {

        TableColumnModel colModel = table.getColumnModel();
        TableColumn col = colModel.getColumn(vColIndex);
        int width = 0;


        TableCellRenderer renderer = col.getHeaderRenderer();
        if (renderer == null) {
            renderer = table.getTableHeader().getDefaultRenderer();
        }
        Component comp =
            renderer.getTableCellRendererComponent(table, col.getHeaderValue(), false, false, 0, 0);
        width = comp.getPreferredSize().width;


        for (int r = 0; r < table.getRowCount(); r++) {
            renderer = table.getCellRenderer(r, vColIndex);
            comp = renderer.getTableCellRendererComponent(table, table.getValueAt(r, vColIndex),
                false, false, r, vColIndex);
            width = Math.max(width, comp.getPreferredSize().width);
        }

        width += 10 * margin;

        col.setPreferredWidth(width);
    }



    private ProgressPanel getProgressPanelEditor() {
        if (progressPanelEditor == null) {
            progressPanelEditor = new ProgressPanel();
        }
        return progressPanelEditor;
    }


    @Override
    public void tableChanged(TableModelEvent e) {
        if (e.getType() == TableModelEvent.UPDATE) {
            this.repaint();

        }
        super.tableChanged(e);
    }

}
