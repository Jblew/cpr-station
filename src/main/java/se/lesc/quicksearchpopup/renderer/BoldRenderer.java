package se.lesc.quicksearchpopup.renderer;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;

import javax.swing.JList;

/**
 * Renders a bold font for every part of the row that matches the search string.
 */ 
public class BoldRenderer extends MatchRenderer {
   
	/**
	 * The additional with to add to a cell when in quick mode. This is because a bold font
	 * takes more space than a plain with. null mean that a new width value should be calculated. 
	 */
    private Integer extraCellWidth;

	public BoldRenderer() {
    }
    
	public void setQuickRenderMode(boolean quickRenderMode) {
		super.setQuickRenderMode(quickRenderMode);
		
		if (quickRenderMode) {
			//Calculate a new extraWidth value during next render
			extraCellWidth = null;
		}
	}
    
	/**
	 * Calculates how much mode space should be allocated becuase of a bold font. A bold font
	 * typically has a large width than a plain font. In some cases (for example capital
	 * 'F') the bold font is narrower.
	 * @param list The list, used to get the font
	 * @return number of pixels to add to the width
	 */
	private int calculateBoldFontWidthDifference(JList list) {
		Font font = list.getFont();
		FontMetrics fontMetrics =  list.getFontMetrics(font);
		
		Font boldFont = font.deriveFont(Font.BOLD);
		FontMetrics boldFontMetrics =  list.getFontMetrics(boldFont);
		
		//Calculate a new extra size for the bold font
		int extraWidth = boldFontMetrics.stringWidth(searchString) - fontMetrics.stringWidth(searchString);
		
		//Add some extra width just to be sure (since capital letters take less space in bold)
		extraWidth += (int) (searchString.length() * 0.5);
		return extraWidth;
	}

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index,
        boolean isSelected, boolean cellHasFocus) {

    	//Reset the preferred width so that the component may recalculate it
    	defaultRenderer.setPreferredSize(null); 
        
    	Component component = defaultRenderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

        if (quickRenderMode) {
        	if (extraCellWidth == null) {
        		extraCellWidth = calculateBoldFontWidthDifference(list);
            	Dimension componentPreferredSize = component.getPreferredSize();
            
            	//Change the preferred width by adding the extra space the bold font takes
            	component.setPreferredSize(new Dimension(
            			componentPreferredSize.width + extraCellWidth,
                		componentPreferredSize.height));
        	}

        	return component;
        } else {
            try {
            	component = renderHook(value.toString(), component);
            } catch (Exception e) {
            	System.err.println("Search string: " + searchString);
            	System.err.println(value.toString());
            	e.printStackTrace();  	
            }
        	return component;
        }
    }

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
    			sb.append("<b>");
    			sb.append(row.substring(matchStart, matchEnd));
    			sb.append("</b>");

    			previousMatchEnd = matchEnd;

    		}

    		sb.append(row.substring(matches[matches.length - 1][1], row.length()));
    		sb.append("</html>");

    		defaultRenderer.setText(sb.toString());
    	}

    	return component;
    }

}
