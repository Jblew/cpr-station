package se.lesc.quicksearchpopup;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AbstractSearcher {

	static int[][] createMatchArray(List<Range> matches) {
		Collections.sort(matches);
		
		matches = compress(matches);

		int[][] matchArray = new int[matches.size()][];
		for (int i = 0; i < matchArray.length; i++) {
			Range matchArea = matches.get(i);
			matchArray[i] = new int[2];
			matchArray[i][0] = matchArea.from;
			matchArray[i][1] = matchArea.to;
		}
		return matchArray;
	}


	/** Compresses (a sorted list) if some ranges overlap */
	static List<Range> compress(List<Range> matches) {
		ArrayList<Range> compressedList = new ArrayList<Range>();

		Range addCandidate = null;
	
		for (int i = 0; i < matches.size(); i++) {

//			boolean addCandiateShouldBeInserted = false;
			
			Range current = matches.get(i);

			if (addCandidate != null) {
				if (addCandidate.intersects(current)) {
					addCandidate = Range.join(current, addCandidate);
//					i++; //Skip next
					//Wait to add this, since more joins might come later
				} else {
//					addCandiateShouldBeInserted = true;
					
					compressedList.add(addCandidate); //Since there is no itersection the add candidate can be added
					//Make the current the new addCandiate
					addCandidate = current;
					
				}
			} else {
				//Make the current the new addCandiate
				addCandidate = current;
			}

//			//Only add to the compressed list if we know that there are no more matches
//			if (addCandiateShouldBeInserted) {
//				compressedList.add(addCandidate);
//				addCandidate = null;
//			}
		}
		
		//Always add the last addCandiate since there are nor more to compare with
		if (addCandidate != null) {
			compressedList.add(addCandidate);
		}

		return compressedList;
	}
	
}
