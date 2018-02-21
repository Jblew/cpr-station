package se.lesc.quicksearchpopup;

/**
 * Text matching algorithm used to search many rows using a single search string. 
 */
public interface Searcher {
	
	boolean matches(String searchString, String matchCanditate);
	
	int[][] matchArea(String searchString, String matchCanditate);
}
