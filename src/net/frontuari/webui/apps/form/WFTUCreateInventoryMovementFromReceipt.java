package net.frontuari.webui.apps.form;

import java.util.ArrayList;
import java.util.Vector;
import java.util.logging.Level;

import net.frontuari.grid.FTUCreateInventoryMovementFromReceipt;

import org.adempiere.exceptions.AdempiereException;
import org.adempiere.webui.apps.AEnv;
import org.adempiere.webui.apps.form.WCreateFromWindow;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.Listbox;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.Rows;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.compiere.apps.IStatusBar;
import org.compiere.minigrid.IMiniTable;
import org.compiere.model.GridTab;
import org.compiere.util.Env;
import org.compiere.util.KeyNamePair;
import org.compiere.util.Msg;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;
import org.zkoss.zul.Vlayout;

public class WFTUCreateInventoryMovementFromReceipt extends
		FTUCreateInventoryMovementFromReceipt implements EventListener<Event>,
		ValueChangeListener {
	
	private WCreateFromWindow window;
	private int p_WindowNo;
	
	private boolean m_actionActive = false;
	
	//Whe have the labels-------------
	Label mInOutLabel = new Label();
	Label mLocatorLabel = new Label();
	//--------------------------------
	
	//Whe have the fields-----------------------------------------
	Listbox mInOutField = ListboxFactory.newDropdownListbox();
	Listbox mLocatorField = ListboxFactory.newDropdownListbox();
	//------------------------------------------------------------
	public WFTUCreateInventoryMovementFromReceipt(GridTab gridTab) {
		super(gridTab);
		log.info(getGridTab().toString());
		
		p_WindowNo = getGridTab().getWindowNo();
		
		window = new WCreateFromWindow(this, p_WindowNo);

		try
		{
			if (!dynInit())
				return;
			zkInit();
			setInitOK(true);
		}
		catch(Exception e)
		{
			log.log(Level.SEVERE, "", e);
			setInitOK(false);
			throw new AdempiereException(e.getMessage());
		}
		AEnv.showWindow(window);
	}

	private void zkInit() {
		
		mInOutLabel.setText(Msg.getElement(Env.getCtx(), "M_InOut_ID"));
		mLocatorLabel.setText(Msg.getElement(Env.getCtx(), "M_Locator_ID"));
		
		Vlayout vlayout = new Vlayout();
		vlayout.setVflex("1");
		vlayout.setWidth("100%");
    	Panel parameterPanel = window.getParameterPanel();
		parameterPanel.appendChild(vlayout);
		
		Grid parameterStdLayout = GridFactory.newGridLayout();
    	vlayout.appendChild(parameterStdLayout);
    	
    	Rows rows = (Rows) parameterStdLayout.newRows();
		Row row = rows.newRow();
		
		mInOutField.setHflex("true");
		row.appendCellChild(mInOutLabel.rightAlign());
		row.appendCellChild(mInOutField);
		
		row = rows.newRow();
		
		mLocatorField.setHflex("true");
		row.appendCellChild(mLocatorLabel.rightAlign());
		row.appendCellChild(mLocatorField);
	}

	@Override
	public Object getWindow() {
		// TODO Auto-generated method stub
		return window;
	}

	@Override
	public void valueChange(ValueChangeEvent evt) {
		// TODO Auto-generated method stub

	}
	
	protected void initMInOut(){
		
		mInOutField.removeActionListener(this);
		mInOutField.removeAllItems();
		
		ArrayList<KeyNamePair> items = loadMInOutData();
		
		for (KeyNamePair item: items)
			mInOutField.addItem(item);
		
		mInOutField.setSelectedIndex(0);
		mInOutField.addActionListener(this);
	}
	
	protected void initLocator() {
		
		mLocatorField.removeActionListener(this);
		mLocatorField.removeAllItems();
		
		ArrayList<KeyNamePair> items = loadMLocatorToData();
		
		for (KeyNamePair item: items)
			mLocatorField.addItem(item);
		
		mLocatorField.setSelectedIndex(0);
		mLocatorField.addActionListener(this);
	}

	@Override
	public boolean dynInit() throws Exception {
		log.config("");
		
		super.dynInit();
		
		window.setTitle(getTitle());
		
		initMInOut();
		
		initLocator();
		
		return true;
	}
	
	

	@Override
	public void info(IMiniTable miniTable, IStatusBar statusBar) {
		// TODO Auto-generated method stub

	}
	
	protected void loadTableOIS (Vector<?> data)
	{
		window.getWListbox().clear();
		
		//  Remove previous listeners
		window.getWListbox().getModel().removeTableModelListener(window);
		//  Set Model
		ListModelTable model = new ListModelTable(data);
		model.addTableModelListener(window);
		window.getWListbox().setData(model, getOISColumnNames());
		//
		
		configureMiniTable(window.getWListbox());
	}   //  loadOrder

	@Override
	public void onEvent(Event evt) throws Exception {
		
		if (m_actionActive)
			return ;
		
		m_actionActive = true;
		
		if (evt.getTarget().equals(mInOutField))
		{
			int M_InOut_ID = mInOutField.getSelectedItem().toKeyNamePair().getKey();
			
			if (M_InOut_ID != 0)
			{
				loadMInOutLines(M_InOut_ID);
			}
		} else if (evt.getTarget().equals(mLocatorField))
			M_Locator_ID = mLocatorField.getSelectedItem().toKeyNamePair().getKey();
		
		m_actionActive = false;
	}
	
	protected void loadMInOutLines(int M_Inout_ID) {
		
		loadTableOIS(getMInOutData(M_Inout_ID));
	}

}
