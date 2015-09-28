package se.lesc.quicksearchpopup;

import java.applet.Applet;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.util.ArrayList;

import javax.swing.Popup;
import javax.swing.PopupFactory;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.JTextComponent;

import se.lesc.quicksearchpopup.renderer.MatchRenderer;

public class QuickSearchPopup implements SelectionListener {

	private JTextComponent searchField;
	private PopupFactory factory;
	private String[] rows;
	private Searcher searcher;
	private Popup popup;
	private QuickSearchPopupContent quickSearchPopupContent;
	private SearchFieldEventListener searchFieldEventListener;
	private ExternalEventListener externalEventListener;
	private SelectionListener parentSelectionListener;
	private MatchRenderer cellRenderer;

	public QuickSearchPopup(JTextComponent searchField, SelectionListener selectionListener) {
		this.searchField = searchField;
		this.parentSelectionListener = selectionListener;
		factory = PopupFactory.getSharedInstance();

		searcher = new WordSearcher(); //Default searcher

		createComponents();

		externalEventListener = new ExternalEventListener();
		searchFieldEventListener = new SearchFieldEventListener();
		searchFieldEventListener.registerForEvents();
	}
	
	protected void handleKey(KeyEvent e) {
		int keyCode = e.getKeyCode();
		if (keyCode == KeyEvent.VK_ESCAPE) {
			hidePopup();
		} else if (keyCode == KeyEvent.VK_ENTER || keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_UP
				|| keyCode == KeyEvent.VK_PAGE_DOWN || keyCode == KeyEvent.VK_PAGE_UP) {
			quickSearchPopupContent.dispatchEvent(e);
		}
	}

	private void search() {

		String searchString = searchField.getText();
		if (searchString.isEmpty()) {
			hidePopup();
			return;
		}

		if (rows != null && rows.length > 0) {
    		ArrayList<String> matchedRows = new ArrayList<String>();
    		for (String row : rows) {
    			if (searcher.matches(searchString, row)) {
    				matchedRows.add(row);
    			}
    		}
    
    		if (cellRenderer != null) {
    			quickSearchPopupContent.setCellRenderer(cellRenderer);
    		}
    		quickSearchPopupContent.setElements(searcher, searchString, matchedRows);
    		quickSearchPopupContent.prepareToShow();
    		show();
		}
	}

	private void createComponents() {
		quickSearchPopupContent = new QuickSearchPopupContent(searchField, searcher, this);
	}

	private Popup createPopup() {
		Point locationOnScreen = searchField.getLocationOnScreen();

		int x = locationOnScreen.x;
		int y = locationOnScreen.y + searchField.getHeight();

//		Logger.getLogger(getClass().getName()).info(x + ", " + y);
		Popup popup = factory.getPopup(searchField, quickSearchPopupContent, x, y);

//		quickSearchPopupContent.setMaximumSize(new Dimension(
//				searchField.getSize().width,
//						quickSearchPopupContent.getPreferredSize().height));
		
		externalEventListener.registerForEvents();

		//		quickSearcherList.addFocusListener(new FocusListener() {
		//
		//			@Override
		//			public void focusLost(FocusEvent e) {
		//				Logger.getLogger(getClass().getName()).info("focusLost");
		//			}
		//
		//			@Override
		//			public void focusGained(FocusEvent e) {
		//				Logger.getLogger(getClass().getName()).info("focusGained");
		//			}
		//		});

		//		JPopupMenu popup = new JPopupMenu();
		//		popup.add(panel);
		//		popup.show(searchField, 0, 0);

		return popup;
	}

	private void show() {
		hidePopup();
		createPopup();
		popup = createPopup();
		popup.show();
	}

	private void hidePopup() {
		if (popup != null) {
			externalEventListener.unRegisterForEvents();
			popup.hide();

			popup = null;
//			Logger.getLogger(getClass().getName()).info("hide");
		}
	}

	public void setSearchRows(String[] rows) {
		this.rows = rows;
	}

	
	/**
	 * This class is responsible to handle every event the user does inside the search field that
	 * could cause a change of the popup. For example if the user presses a key the popup should be
	 * shown and a search executed.
	 */
	private class SearchFieldEventListener {
		
		DocumentListener documentListener;
		KeyListener keyListener;
		
		public void registerForEvents() {
			documentListener =  new DocumentListener() {
				@Override
				public void removeUpdate(DocumentEvent e) {
					search();
				}
				
				@Override
				public void insertUpdate(DocumentEvent e) {
					search();
				}
				
				@Override
				public void changedUpdate(DocumentEvent e) {
					search();
				}
			};
			searchField.getDocument().addDocumentListener(documentListener);
			
			keyListener = new KeyAdapter() {
				@Override
				public void keyPressed(KeyEvent e) {
					handleKey(e);
				}
			};
			searchField.addKeyListener(keyListener);
		}
		
		//TODO: not sure when to call this method.
		public void unRegisterForEvents() {
			searchField.getDocument().removeDocumentListener(documentListener);
			searchField.removeKeyListener(keyListener);
		}
	}
	
	/**
	 * This class is responsible to hide the popup in case of an external event that should close
	 * the popup. For example if the Window is minimized or the user clicks outside of the popup.
	 */
	private class ExternalEventListener implements AWTEventListener {

		/** The Windows object the the searchField resides in */
		private Window searchFieldWindow;

		private ComponentAdapter searchFieldWindowComponentListener;

		public void registerForEvents() {
			final Toolkit toolkit = Toolkit.getDefaultToolkit();
			java.security.AccessController.doPrivileged(
					new java.security.PrivilegedAction<Void>() {
						public Void run() {
							toolkit.addAWTEventListener(ExternalEventListener.this,
									AWTEvent.MOUSE_EVENT_MASK |
									AWTEvent.MOUSE_MOTION_EVENT_MASK |
									AWTEvent.MOUSE_WHEEL_EVENT_MASK |
									AWTEvent.WINDOW_EVENT_MASK
									);
							return null;
						}
					}
			);
			
			searchFieldWindow = findParentWindow(searchField);
			searchFieldWindowComponentListener = new ComponentAdapter() {
				@Override
				public void componentResized(ComponentEvent e) {
					hidePopup();
				}

				@Override
				public void componentMoved(ComponentEvent e) {
					hidePopup();
				}
			};			
			searchFieldWindow.addComponentListener(searchFieldWindowComponentListener);
		}

		public void unRegisterForEvents() {
			final Toolkit toolkit = Toolkit.getDefaultToolkit();
			java.security.AccessController.doPrivileged(
					new java.security.PrivilegedAction<Void>() {
						public Void run() {
							toolkit.removeAWTEventListener(ExternalEventListener.this);
							return null;
						}
					}
			);
			searchFieldWindow.removeComponentListener(searchFieldWindowComponentListener);
		}

		@Override
		public void eventDispatched(AWTEvent event) {
//			Logger.getLogger(getClass().getName()).info(event);
			
//			if (event instanceof ComponentEvent) {
//				ComponentEvent componentEvent = (ComponentEvent) event;
//				if (event.getID() == ComponentEvent.COMPONENT_MOVED) {
//					hidePopup();
//				}
//			}
			
			if (event instanceof WindowEvent) {
//				WindowEvent windowEvent = (WindowEvent) event;
//				Logger.getLogger(getClass().getName()).info(windowEvent);
				if (event.getID() == WindowEvent.WINDOW_LOST_FOCUS) {
					hidePopup();
//				} else {
//					Logger.getLogger(getClass().getName()).info(windowEvent);
				}
			}

			if (event instanceof MouseEvent) {
				handleMouseEvent((MouseEvent) event);
			}

//			if (event instanceof FocusEvent) {
//				FocusEvent focusEvent = (FocusEvent) event;
////				Logger.getLogger(getClass().getName()).info(focusEvent);
//				hidePopup();
//			}
		}

		private void handleMouseEvent(MouseEvent event) {
			MouseEvent mouseEvent = (MouseEvent) event;
			Component source = mouseEvent.getComponent();
			switch (mouseEvent.getID()) {
			case MouseEvent.MOUSE_PRESSED:
				if (isInPopup(source)) {
					return;
				}

//				Logger.getLogger(getClass().getName()).info(event);
				hidePopup();
				break;

			case MouseEvent.MOUSE_RELEASED:
				// Do not forward event to MSM, let component handle it
				if (isInPopup(source)) {
					break;
				}
				break;
				//            case MouseEvent.MOUSE_DRAGGED:
					//            	if(!(src instanceof MenuElement)) {
				//            		// For the MOUSE_DRAGGED event the src is
				//            		// the Component in which mouse button was pressed. 
				//            		// If the src is in popupMenu, 
				//            		// do not forward event to MSM, let component handle it.
				//            		if (isInPopup(src)) {
				//            			break;
				//            		}
				//            	}
				//            	MenuSelectionManager.defaultManager().
				//            	processMouseEvent(me);
				//            	break;
			case MouseEvent.MOUSE_WHEEL:
				if (isInPopup(source)) {
					return;
				}
				hidePopup();
				break;
			}
		}

		boolean isInPopup(Component src) {
			for (Component c=src; c!=null; c=c.getParent()) {
				if (c instanceof Applet || c instanceof Window) {
					break;
				} else if (c instanceof QuickSearchPopupContent) {
					return true;
				}
			}
			return false;
		}

		
		
		private Window findParentWindow(Component component) {
			if (component instanceof Window) {
				return (Window) component;
			} else {
				Container parent = component.getParent();
				if (parent == null) {
					return null;
				} else {
					return findParentWindow(parent);
				}
			}
		}
	}

	@Override
	public void rowSelected(String row) {
		parentSelectionListener.rowSelected(row);
		hidePopup();
	}


	public void setMatchRenderer(MatchRenderer newRenderer) {
		this.cellRenderer = newRenderer;
	}

}