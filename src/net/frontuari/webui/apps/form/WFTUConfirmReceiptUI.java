package net.frontuari.webui.apps.form;

import java.util.Vector;

import org.adempiere.webui.component.Borderlayout;
import org.adempiere.webui.component.Button;
import org.adempiere.webui.component.ConfirmPanel;
import org.adempiere.webui.component.Grid;
import org.adempiere.webui.component.GridFactory;
import org.adempiere.webui.component.Label;
import org.adempiere.webui.component.ListModelTable;
import org.adempiere.webui.component.ListboxFactory;
import org.adempiere.webui.component.Panel;
import org.adempiere.webui.component.Row;
import org.adempiere.webui.component.WListbox;
import org.adempiere.webui.editor.WSearchEditor;
import org.adempiere.webui.editor.WTableDirEditor;
import org.adempiere.webui.event.ValueChangeEvent;
import org.adempiere.webui.event.ValueChangeListener;
import org.adempiere.webui.event.WTableModelEvent;
import org.adempiere.webui.event.WTableModelListener;
import org.adempiere.webui.factory.ButtonFactory;
import org.adempiere.webui.panel.StatusBarPanel;
import org.adempiere.webui.window.FDialog;
import org.compiere.model.Lookup;
import org.compiere.model.MColumn;
import org.compiere.model.MLookupFactory;
import org.compiere.model.X_M_InOut;
import org.compiere.model.X_M_Product;
import org.compiere.util.DisplayType;
import org.compiere.util.Env;
import org.compiere.util.Msg;
import org.compiere.util.Trx;
import org.compiere.util.TrxRunnable;
import org.zkoss.zk.ui.Component;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zul.Separator;

import net.frontuari.grid.FTUConfirmReceiptController;

public class WFTUConfirmReceiptUI extends FTUConfirmReceiptController implements WTableModelListener, ValueChangeListener {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	//We have the Name of Columns Constants-------------------
	public static final String M_IOUT_COLUMN = "M_Inout_ID";
	public static final String M_PRODUCT_COLUMN = "M_Product_ID";
	//--------------------------------------------------------
	
	//We Have Parameters Fields-----------------------------------------
	private WTableDirEditor inOutField;
	private Label inOutLabel = new Label();
	
	private Label productLabel = new Label();
	private WSearchEditor productField;
	//-------------------------------------------------------------------
	
	//We have the Components of Form-------------------------------------
	private WListbox miniTable = ListboxFactory.newDataTable();
	private Borderlayout mainPanel = new Borderlayout();
	private Grid parameterPane = GridFactory.newGridLayout();
	private ConfirmPanel confirm = new ConfirmPanel(false, false, false, false, false, false);
	private Button ok;
	private StatusBarPanel statusBar = new StatusBarPanel();
	private Button confirmLine;
	//-------------------------------------------------------------------
	
	public WFTUConfirmReceiptUI() {
		
		super();
		
		try {
			dynInit();
			zkInit();
		} catch(Exception e){
			FDialog.error(m_WindowNo, this, e.getMessage());
		}
	}
	
	public void dynInit() throws Exception {
		
		setTitle(Msg.getMsg(Env.getCtx(), "Confirm.Receipt"));
		ok = confirm.getButton(ConfirmPanel.A_OK);
		
		confirmLine = ButtonFactory.createNamedButton("Confirm.Line.Receipt", true, false);
		confirm.addComponentsCenter(confirmLine);
		inOutLabel.setText(Msg.getElement(Env.getCtx(), M_IOUT_COLUMN, false));
		productLabel.setText(Msg.translate(Env.getCtx(), M_PRODUCT_COLUMN));
		
		ok.setDisabled(true);
		ok.addActionListener(this);
		ok.setTooltiptext(Msg.getMsg(Env.getCtx(), "Complete.Receipt"));
		confirmLine.setDisabled(true);
		confirmLine.addActionListener(this);
		
		//-----------------------------------------------Set Shipment---------------------------------------------------------------
		StringBuffer where = new StringBuffer("M_InOut.IsActive = 'Y' AND M_InOut.DocStatus IN ('DR','PR','IN') AND M_InOut.MovementType IN ('V+')")
								.append(" AND EXISTS (SELECT 1 FROM C_DocType dt")
								.append(" 		WHERE dt.C_Doctype_ID = M_InOut.C_Doctype_ID AND dt.IsConsignmentDocument = 'Y')")
								.append(" AND EXISTS (SELECT 1 FROM M_InOutLine iol WHERE iol.M_InOut_ID = M_InOut.M_InOut_ID")
								.append("				AND iol.IsActive = 'Y')");
		
		int columnId = MColumn.getColumn_ID(X_M_InOut.Table_Name, X_M_InOut.COLUMNNAME_M_InOut_ID);
		Lookup lookup = MLookupFactory.get(Env.getCtx(), m_WindowNo, columnId, DisplayType.TableDir, Env.getLanguage(Env.getCtx()),
				X_M_InOut.COLUMNNAME_M_InOut_ID, 0, true, where.toString());
		
		inOutField = new WTableDirEditor(X_M_InOut.COLUMNNAME_M_InOut_ID, true, false, true, lookup);
		Env.setContext(Env.getCtx(), m_WindowNo, X_M_InOut.COLUMNNAME_M_InOut_ID, 0);
		inOutField.addValueChangeListener(this);
		//---------------------------------------------------------------------------------------------------------------------------
		
		//-------------------------------------------------Set Product---------------------------------------------------------------
		where = new StringBuffer("M_Product.IsActive = 'Y' AND M_Product.IsConsignmentProduct = 'Y'")
					.append(" AND EXISTS (SELECT 1 FROM M_InOutLine iol WHERE iol.M_InOut_ID = @M_InOut_ID@")
					.append("				AND iol.M_Product_ID = M_Product.M_Product_ID AND iol.IsActive = 'Y'")
					.append(" 				AND iol.IsConfirmed = 'N')");
		
		columnId = MColumn.getColumn_ID(X_M_Product.Table_Name, X_M_Product.COLUMNNAME_M_Product_ID);
		lookup = MLookupFactory.get(Env.getCtx(), m_WindowNo, columnId, DisplayType.Search, Env.getLanguage(Env.getCtx()),
				X_M_Product.COLUMNNAME_M_Product_ID, 1000283, true, where.toString());
		
		productField = new WSearchEditor("M_Product_ID", false, false, true, lookup);
		productField.addValueChangeListener(this);
		//---------------------------------------------------------------------------------------------------------------------------
		setStatusBar(statusBar);
	}
	
	public void zkInit() {
		
		appendChild(mainPanel);
		
		mainPanel.setWidth("90%");
		mainPanel.setHeight("100%");
		mainPanel.setStyle("margin:auto");
		
		mainPanel.appendNorth(parameterPane);
		//----We have Rows and Columns Of Parameter Pane----
		Row row = parameterPane.newRows().newRow();
		
		parameterPane.setStyle("margin: 0 0 .5rem 0 !important");
		row.appendCellChild(inOutLabel.rightAlign());
		inOutField.getComponent().setHflex("true");
		row.appendCellChild(inOutField.getComponent(), 2);
		
		/*row = new Row();
		parameterPane.getRows().appendChild(row);*/
		row.appendCellChild(productLabel.rightAlign());
		productField.getComponent().setHflex("true");
		row.appendCellChild(productField.getComponent(),2);
		
		mainPanel.appendCenter(miniTable);
		miniTable.setWidth("100%");
		//miniTable.setStyle("border:none");
		
		Panel southPanel = new Panel();
		
		southPanel.appendChild(new Separator());
		southPanel.appendChild(confirm);
		
		southPanel.appendChild(new Separator());
		southPanel.appendChild(statusBar);
		
		statusBar.setStyle("text-align:center");
		
		mainPanel.appendSouth(southPanel);
		//--------------------------------------------------
	}
	
	protected void loadTableOIS (Vector<?> data) {
		miniTable.clear();
		
		//  Remove previous listeners
		miniTable.getModel().removeTableModelListener(this);
		//  Set Model
		ListModelTable model = new ListModelTable(data);
		model.addTableModelListener(this);
		miniTable.setData(model, getOISColumnNames());
		//
		
		configureMiniTable(miniTable);
		miniTable.setWidth("100%");
	}
	
	

	@Override
	public void onEvent(Event event) throws Exception {
		
		Component target = event.getTarget();
		
		if (target.equals(ok))
		{
			try
			{
				Trx.run(new TrxRunnable() {
					
					@Override
					public void run(String trxName)
					{
						completeInOut(trxName);
						resetFields();
						FDialog.info(m_WindowNo, WFTUConfirmReceiptUI.this, Msg.getMsg(Env.getCtx(), "Receipt.Completed"));
					}
				});
				
			} catch (Exception e){
				FDialog.error(m_WindowNo, this, e.getMessage());
				resetFields();
			}
		}
		else if (target.equals(confirmLine))
		{
			try
			{
				Trx.run(new TrxRunnable() {
					
					@Override
					public void run(String trxName)
					{
						confirmLine(miniTable, trxName);
						
						resetProduct();
						linesConfirmed++;
						linesUnconfirmed--;
						ok.setEnabled(linesUnconfirmed == 0);
						setStatusBar(statusBar);
					}
				});
				
			} catch (Exception e){
				FDialog.error(m_WindowNo, this, e.getMessage());
				resetFields();
			}
		}
		else
			super.onEvent(event);
	}
	
	private void resetProduct() {
		
		m_Product_ID = 0;
		
		productField.setValue(null);
		
		confirmLine.setDisabled(true);
		
		loadTableOIS(new Vector<>());
	}

	@Override
	protected void initForm() {
		
	}

	@Override
	public void tableChanged(WTableModelEvent event) {
		
		int currRow = event.getLastRow();
		int column = event.getColumn();
		
		if (column != 0)
			return ;
		
		confirmLine.setEnabled(((Boolean) miniTable.getValueAt(currRow, column)).booleanValue());
	}
	
	protected void resetFields() {
		
		m_InOut_ID = 0;
		
		inOutField.setValue(null);
		inOutField.actionRefresh();
		
		linesConfirmed = 0;
		linesUnconfirmed = 0;
		
		ok.setDisabled(true);
		resetProduct();
		setStatusBar(statusBar);
	}

	@Override
	public void valueChange(ValueChangeEvent evt) {
		
		String name = evt.getPropertyName();
		Object value = evt.getNewValue();
		
		if (value == null || value.equals(evt.getOldValue()) || value instanceof Integer[])
			return ;
		
		if (X_M_InOut.COLUMNNAME_M_InOut_ID.equals(name))
		{
			m_InOut_ID = value != null ? ((Integer) value).intValue() : 0;
			Env.setContext(Env.getCtx(), m_WindowNo, X_M_InOut.COLUMNNAME_M_InOut_ID, m_InOut_ID);
			productField.setValue(null);
			
			setNumberLines();
			setStatusBar(statusBar);
			
			resetProduct();
			ok.setEnabled(linesUnconfirmed == 0);
		} else if (X_M_Product.COLUMNNAME_M_Product_ID.equals(name))
		{
			m_Product_ID = (Integer) value;
			confirmLine.setEnabled(true);
			loadTableOIS(loadMInOutLineData());
		}
	}
}
