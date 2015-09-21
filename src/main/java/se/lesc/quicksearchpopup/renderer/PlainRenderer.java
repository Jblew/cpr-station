package se.lesc.quicksearchpopup.renderer;

import java.awt.Component;

/**
 * Does not apply any special highlight of what part of the user search string has matched the row. 
 */
public class PlainRenderer extends MatchRenderer {
	@Override
	protected Component renderHook(String string, Component component) {
		return component; //Do nothing
	}
}
