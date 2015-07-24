/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package pl.jblew.cpr.gui.components;

import java.awt.Graphics;
import java.awt.Rectangle;
import javax.swing.JTabbedPane;
import javax.swing.plaf.basic.BasicTabbedPaneUI;

/**
 *
 * @author teofil
 */
public class NoTabTabbedPaneUI extends BasicTabbedPaneUI {
    @Override
    protected int calculateTabAreaHeight(int tabPlacement, int horizRunCount, int maxTabHeight) {
        return 0;
    }

    @Override
    protected void paintTab(Graphics g, int tabPlacement, Rectangle[] rects, int tabIndex, Rectangle iconRect, Rectangle textRect) {

    }

    @Override
    protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
    }

    @Override
    public int tabForCoordinate(JTabbedPane pane, int x, int y) {
        return -1;
    }
}
