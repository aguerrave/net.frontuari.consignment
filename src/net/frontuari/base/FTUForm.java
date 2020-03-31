/**
 * 
 */
package net.frontuari.base;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.adempiere.webui.panel.ADForm;
import org.adempiere.webui.panel.IFormController;
import org.compiere.util.DB;
import org.compiere.util.KeyNamePair;
import org.zkoss.zk.ui.event.Event;
import org.zkoss.zk.ui.event.EventListener;

/**
 * Class for dynamic model factory
 * 
 * @author Dixon Martinez, dixonalvarezm@gmail.com, http://www.dixonmartinez.com.ve
 *
 */
public abstract class FTUForm extends ADForm implements IFormController, EventListener<Event> {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2011154708990879053L;

	/* (non-Javadoc)
	 * @see org.adempiere.webui.panel.IFormController#getForm()
	 */
	@Override
	public ADForm getForm() {
		return this;
	}
	
	public List<KeyNamePair> getKeyNamePairData(String sql, List<Object> params) {
		
		return getKeyNamePairData(sql, params.toArray( new Object[params.size()] ));
	}
	
	public List<KeyNamePair> getKeyNamePairData(String sql, Object ...params) {
		
		ArrayList<KeyNamePair> data = new ArrayList<>(12);
		
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		
		try {
			pstmt = DB.prepareStatement(sql, null);
			
			for (int i = 0; i < params.length; i++)
				pstmt.setObject(i + 1, params[i]);
			
			rs = pstmt.executeQuery();
			
			while (rs.next())
				data.add(new KeyNamePair(rs.getInt(1), rs.getString(2)));
			
		} catch (SQLException e) {
			
		} finally {
			DB.close(rs, pstmt);
		}
		
		return data;
	}
}
