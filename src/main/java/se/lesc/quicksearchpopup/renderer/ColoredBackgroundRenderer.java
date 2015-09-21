package se.lesc.quicksearchpopup.renderer;

import java.awt.Component;

/**
 * Renders a a colored background for every part of the row that matches the search string.
 */ 
public class ColoredBackgroundRenderer extends MatchRenderer {
	
	private String color = "yellow";

    protected Component renderHook(String row, Component component) {

    	int[][] matches = searcher.matchArea(searchString, row);

    	if (matches != null) {
    		StringBuilder sb = new StringBuilder();
    		sb.append("<html>");

    		int i = 0;
    		int previousMatchEnd = 0;
    		for (; i < matches.length; i++) {
    			int[] match = matches[i];
    			int matchStart = match[0];
    			int matchEnd = match[1];

    			sb.append(row.substring(previousMatchEnd, matchStart));
    			sb.append("<font style=\"background-color: " + color + ";\">");
    			sb.append(row.substring(matchStart, matchEnd));
    			sb.append("</font>");

    			previousMatchEnd = matchEnd;
    		}

    		sb.append(row.substring(matches[matches.length - 1][1], row.length()));
    		sb.append("</html>");

    		defaultRenderer.setText(sb.toString());
    	}

    	return component;
    }

}
