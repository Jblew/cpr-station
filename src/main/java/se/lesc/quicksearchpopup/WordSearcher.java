package se.lesc.quicksearchpopup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Searcher that uses each word (separated by space) to search in a AND fashion. 
 */
public class WordSearcher extends AbstractSearcher implements Searcher {

	private boolean caseSensitive;
	
	public void setCaseSensitive(boolean caseSensitive) {
		this.caseSensitive = caseSensitive;
	}
	
	@Override
	public boolean matches(String searchString, String matchCanditate) {
//		return matchArea(searchString, matchCanditate) != null;
		
		if (! caseSensitive) {
			searchString = searchString.toLowerCase();
			matchCanditate = matchCanditate.toLowerCase();
		}
		String[] words = searchString.split(" ");
		
		if (words.length == 0) {
			return false;
		}

		for (String word : words) {
			if (! matchCanditate.contains(word)) {
				return false;
			}
		}
		return true;
	}
	
	@Override
	public int[][] matchArea(String searchString, String matchCanditate) {
		String[] words = searchString.split(" ");
		
		List<Range> matches = new ArrayList<Range>();
		
		int caseSensitiveFlags = 0;
		if (! caseSensitive) {
			caseSensitiveFlags = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		}
		
		for (String word : words) {
			boolean hasMatchedWord = matchWord(matchCanditate, matches, caseSensitiveFlags, word);
			if (! hasMatchedWord) {
				return null; //Every word must be matched
			}
		}
		
		int[][] matchArray = createMatchArray(matches);
		
		return matchArray;
	}




	private boolean matchWord(String matchCanditate, List<Range> matches,
			int caseSensitiveFlags, String word) {
		Pattern pattern = Pattern.compile(".*?(" + Pattern.quote(word) + ").*?", caseSensitiveFlags);
		Matcher matcher = pattern.matcher(matchCanditate);
		
		boolean hasMatched = false;
		
		while (matcher.find()) {
			hasMatched = true;
			matches.add(new Range(matcher.start(1), matcher.end(1)));				
		}
		return hasMatched;
	}
	


}
