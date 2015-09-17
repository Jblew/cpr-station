/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.panels;

import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingUtilities;
import pl.jblew.cpr.bootstrap.Context;
import pl.jblew.cpr.gui.MainPanel;
import pl.jblew.cpr.gui.util.PanelDisabler;
import pl.jblew.cpr.gui.util.SpacerPanel;
import pl.jblew.cpr.logic.Event_Localization;
import pl.jblew.cpr.logic.integritycheck.CarrierIntegrityChecker;
import pl.jblew.cpr.logic.io.Repairer;
import pl.jblew.cpr.util.NamingThreadFactory;

/**
 *
 * @author teofil
 */
public class RepairPanel extends MainPanel {
    private final ExecutorService cachedExecutor = Executors.newCachedThreadPool(new NamingThreadFactory("RepairPanel-solutionExecutor"));

    private RepairPanel(Context context, CarrierIntegrityChecker.FilesMissingOnCarrier missingFiles) {
        Repairer repairer = new Repairer(context, missingFiles);
        Map<Event_Localization, Repairer.Solution[]> solutionsMap = repairer.calculateSolutions();

        SwingUtilities.invokeLater(() -> {
            setLayout(new BoxLayout(this, BoxLayout.PAGE_AXIS));

            for (Event_Localization el : solutionsMap.keySet()) {
                Repairer.Solution[] solutions = solutionsMap.get(el);

                JPanel repairElPanel = new JPanel();
                repairElPanel.setLayout(new BorderLayout());

                repairElPanel.add(new JLabel("<html>Uszkodzone wydarzenie <b>" + el.getOrLoadFullEvent(context).getName() + "</b> "
                        + "na nośniku: <b>" + el.getCarrier(context).getName() + "</b>.<br /> Opcje naprawy:"), BorderLayout.CENTER);

                JPanel buttonPanel = new JPanel();

                for (Repairer.Solution solution : solutions) {
                    final JButton solutionButton = new JButton(solution.name);
                    solutionButton.addActionListener((evt) -> {
                        int option = JOptionPane.showConfirmDialog(
                                context.frame,
                                solution.ask,
                                "Potwierdź",
                                JOptionPane.YES_NO_OPTION);

                        if (option == JOptionPane.YES_OPTION) {

                            cachedExecutor.submit(() -> {
                                SwingUtilities.invokeLater(() -> {
                                    solutionButton.setEnabled(false);
                                });

                                try {
                                    String returnMsg = solution.task.call();
                                    SwingUtilities.invokeLater(() -> {
                                        solutionButton.setEnabled(false);
                                        repairElPanel.add(new JLabel(returnMsg));
                                        PanelDisabler.setEnabled(repairElPanel, false);
                                        repairElPanel.revalidate();
                                        repairElPanel.repaint();
                                    });
                                } catch (Repairer.Solution.TryAgainException ex) {
                                    Logger.getLogger(RepairPanel.class.getName()).log(Level.SEVERE, null, ex.getCause());
                                    
                                    SwingUtilities.invokeLater(() -> {
                                        solutionButton.setEnabled(true);
                                        repairElPanel.add(new JLabel(ex.getCause().getMessage()));
                                        repairElPanel.revalidate();
                                        repairElPanel.repaint();
                                    });
                                    
                                } catch (Exception ex) {
                                    Logger.getLogger(RepairPanel.class.getName()).log(Level.SEVERE, null, ex);
                                    SwingUtilities.invokeLater(() -> {
                                        solutionButton.setEnabled(false);
                                        JLabel errLabel = new JLabel(ex.getMessage());
                                        errLabel.setForeground(Color.RED);
                                        repairElPanel.add(errLabel);
                                        repairElPanel.revalidate();
                                        repairElPanel.repaint();
                                    });
                                }

                            });
                        }
                    });

                    buttonPanel.add(solutionButton);
                }

                repairElPanel.add(buttonPanel, BorderLayout.SOUTH);

                add(repairElPanel);
                add(new JSeparator(JSeparator.HORIZONTAL));
            }

            add(new SpacerPanel());
        });

    }

    @Override
    public void activate() {
    }

    @Override
    public void inactivate() {
    }

}
