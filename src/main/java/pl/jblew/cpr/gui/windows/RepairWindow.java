/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.windows;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.components.ErrorLabel;
import pl.jblew.cpr.gui.panels.RepairPanel;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.gui.util.SpacerPanel;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.integritycheck.CarrierIntegrityChecker;
import pl.jblew.cpr.logic.io.Repairer;

/**
 *
 * @author teofil
 */
public class RepairWindow {
    private final CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles;
    private final Context context;
    private final JFrame frame;
    private final AtomicBoolean windowCloseEnabled = new AtomicBoolean(true);

    public RepairWindow(Context context, CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles) {
        this.missingFiles = missingFiles;
        this.context = context;

        this.frame = new JFrame("Naprawianie nośnika \"" + missingFiles.carrier.getName() + "\"");

        SwingUtilities.invokeLater(() -> {
            frame.setSize(500, 500);
            frame.setLocationRelativeTo(null);
            frame.setContentPane(new JScrollPane(new MainPanel()));
            frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
            frame.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent evt) {
                    if (windowCloseEnabled.get()) {
                        frame.setVisible(false);
                    }
                    else {
                        frame.setVisible(true);
                    }
                }
            });

            frame.setVisible(true);
        });

    }

    private final class MainPanel extends JPanel {
        public MainPanel() {
            Repairer repairer = new Repairer(context, missingFiles);
            Map<Event_Localization, Repairer.Solution[]> solutionsMap = repairer.calculateSolutions();

            SwingUtilities.invokeLater(() -> {
                setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

                for (Event_Localization el : solutionsMap.keySet()) {
                    Repairer.Solution[] solutions = solutionsMap.get(el);

                    JPanel repairElPanel = new JPanel();
                    repairElPanel.setLayout(new BorderLayout());

                    repairElPanel.add(new JLabel("<html><p width=480>Uszkodzone wydarzenie <b>" + el.getOrLoadFullEvent(context).getName() + "</b> "
                            + "na nośniku: <b>" + el.getCarrier(context).getName() + "</b>.<br /> Opcje naprawy:</p>"), BorderLayout.NORTH);

                    JPanel buttonPanel = new JPanel();
                    JPanel resultPanel = new JPanel();

                    for (Repairer.Solution solution : solutions) {
                        final JButton solutionButton = new JButton(solution.name);
                        solutionButton.addActionListener((evt) -> {
                            int option = JOptionPane.showConfirmDialog(
                                    frame,
                                    solution.ask,
                                    "Potwierdź",
                                    JOptionPane.YES_NO_OPTION);

                            if (option == JOptionPane.YES_OPTION) {

                                context.cachedExecutor.submit(() -> {
                                    SwingUtilities.invokeLater(() -> {
                                        solutionButton.setEnabled(false);
                                        windowCloseEnabled.set(false);
                                    });

                                    try {
                                        String returnMsg = solution.task.call();
                                        SwingUtilities.invokeLater(() -> {
                                            solutionButton.setEnabled(false);
                                            resultPanel.add(new JLabel(returnMsg));
                                            PanelDisabler.setEnabled(repairElPanel, false);
                                            resultPanel.revalidate();
                                            resultPanel.repaint();
                                            windowCloseEnabled.set(true);
                                        });
                                    } catch (Repairer.Solution.TryAgainException ex) {
                                        Logger.getLogger(RepairPanel.class.getName()).log(Level.SEVERE, null, ex.getCause());

                                        SwingUtilities.invokeLater(() -> {
                                            solutionButton.setEnabled(true);
                                            resultPanel.add(new ErrorLabel(ex.getCause().getMessage()));
                                            resultPanel.revalidate();
                                            resultPanel.repaint();
                                            windowCloseEnabled.set(true);
                                        });

                                    } catch (Exception ex) {
                                        Logger.getLogger(RepairPanel.class.getName()).log(Level.SEVERE, null, ex);
                                        SwingUtilities.invokeLater(() -> {
                                            solutionButton.setEnabled(false);
                                            JLabel errLabel = new ErrorLabel(ex.getMessage());
                                            errLabel.setForeground(Color.RED);
                                            resultPanel.add(errLabel);
                                            resultPanel.revalidate();
                                            resultPanel.repaint();
                                            windowCloseEnabled.set(true);
                                        });
                                    }

                                });
                            }
                        });

                        buttonPanel.add(solutionButton);
                    }

                    repairElPanel.add(buttonPanel, BorderLayout.CENTER);
                    repairElPanel.add(resultPanel, BorderLayout.SOUTH);

                    add(repairElPanel);
                    add(new JSeparator(JSeparator.HORIZONTAL));
                }

                add(new SpacerPanel());
            });
        }
    }
}
