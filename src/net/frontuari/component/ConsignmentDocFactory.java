package net.frontuari.component;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.logging.Level;

import net.frontuari.acct.Doc_FTUInOut;

import org.adempiere.base.DefaultDocumentFactory;
import org.adempiere.base.IDocFactory;
import org.compiere.acct.Doc;
import org.compiere.model.MAcctSchema;
import org.compiere.model.MInOut;
import org.compiere.model.MTable;
import org.compiere.util.CLogger;
import org.compiere.util.DB;
import org.compiere.util.Env;

public class ConsignmentDocFactory implements IDocFactory{
	private final static CLogger s_log = CLogger.getCLogger(DefaultDocumentFactory.class);

	@Override
	public Doc getDocument(MAcctSchema as, int AD_Table_ID, int Record_ID,
			String trxName) {
		
		String tableName = MTable.getTableName(Env.getCtx(), AD_Table_ID);
		Doc doc = null;
		StringBuffer sql = new StringBuffer("SELECT * FROM ")
			.append(tableName)
			.append(" WHERE ").append(tableName).append("_ID = ? AND Processed = 'Y'");
		PreparedStatement pstmt = null;
		ResultSet rs = null;
		try{
			pstmt = DB.prepareStatement(sql.toString(),trxName);
			pstmt.setInt(1, Record_ID);
			rs = pstmt.executeQuery();
			if(rs.next()){
				doc = getDocument(as, AD_Table_ID, rs, trxName);
			}else{
				s_log.severe("Not Found:"+tableName+"_ID="+Record_ID);
			}
			
		}catch(Exception ex){
			
			s_log.log(Level.SEVERE, sql.toString(), ex);
			
		}finally{
			DB.close(rs, pstmt);
			rs = null;
			pstmt = null;
		}
		
		return doc;
	}

	@Override
	public Doc getDocument(MAcctSchema as, int AD_Table_ID, ResultSet rs,
			String trxName) {
		Doc doc = null;
		String tableName = MTable.getTableName(Env.getCtx(), AD_Table_ID);
		
		if(tableName.equals(MInOut.Table_Name)){
			doc = new Doc_FTUInOut(as, rs, trxName);
		}
		
		return doc;
	}

}
