package se.lesc.quicksearchpopup;

/**
 * Callback to know when the user has selected a row 
 */
public interface SelectionListener {

    /**
     * The user has selected a row
     * @param row the selected row
     */
	void rowSelected(String row);
}
