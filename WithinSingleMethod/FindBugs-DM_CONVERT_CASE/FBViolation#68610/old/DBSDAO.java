package br.com.dbsoft.io;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import javax.faces.context.FacesContext;
import javax.faces.event.PhaseId;
import javax.faces.model.DataModelEvent;
import javax.faces.model.DataModelListener;
import javax.servlet.jsp.jstl.sql.Result;
import javax.servlet.jsp.jstl.sql.ResultSupport;

import br.com.dbsoft.annotation.DBSTableModel;
import br.com.dbsoft.core.DBSSDK.IO.DATATYPE;
import br.com.dbsoft.error.DBSIOException;
import br.com.dbsoft.util.DBSIO;
import br.com.dbsoft.util.DBSIO.MOVE_DIRECTION;
import br.com.dbsoft.util.DBSObject;
import br.com.dbsoft.util.DBSString;


/**
 * @param <DataModelClass> Classe Model da tabela do banco de dados ou classe com atributos homônimos as colunas com as quais se deseje trabalhar no DAO.<br/>
 * É necessário também passar esta classe no construtor.  
 */
/**
 * @author ricardo.villar
 *
 * @param <DataModelClass>
 */
public class DBSDAO<DataModelClass> extends DBSDAOBase<DataModelClass> {

	private static final long serialVersionUID = -3273102169619413848L;

	public static enum COMMAND{
    	INSERT,
    	UPDATE,
    	DELETE,
    	SELECT,
    	LOCK;
    } 
    
	//######################################################################################################### 
	//## Working Variables                                                                                    #
	//#########################################################################################################
	private Connection			wConnection;
	private String 				wQuerySQL = "";
	//private ResultSet 			wSelectResultSet;
	private String 				wUK = "";				//Colunas da pesquisa, separada por virgula, que será usadas para retornar ao mesmo registro em caso de refresh
	private String 				wPK = "";
	private String[] 			wPKs = new String[]{};
	private String[] 			wUKs = new String[]{};
	private ResultSetMetaData	wQueryResultSetMetaData;
	private String 				wQuerySQLUK="";						//Query SQL alterada a partir da wsQuerySql, para conter a informação necessários para o controle dos registros
	private DBSRow  			wQueryColumns = new DBSRow();		//Colunas existente na pesquisa
	private DBSRow 				wCommandColumns = new DBSRow();		//Colunas que sofreráo modificação de dados
	private String 				wCommandTableName = "";				//Nome da tabela que sofrerá modificação de dados
	private boolean				wAutoIncrementPK = true;			//Efetua o insert e recuperar o valores do campos com autoincrement;
	private DBSResultDataModel	wResultDataModel;
	private int					wCurrentRowIndex = -1;
	private boolean				wIsMerging = false;

	private DataModelListener 	wDataModelListener = new DataModelListener(){
		@Override
		public void rowSelected(DataModelEvent pEvent) {
			if (FacesContext.getCurrentInstance() == null){return;}
			PhaseId xId = FacesContext.getCurrentInstance().getCurrentPhaseId();
//			System.out.println("PahseId:" + xId.toString());
//			System.out.println("PahseId:" + getQuerySQL());
//			System.out.println("PahseId:" + FacesContext.getCurrentInstance().getPartialViewContext().isAjaxRequest() + ":" + xId.toString() + "[" + wCurrentRowIndex + ":" + pEvent.getRowIndex() + "]");

//				if ((!xId.equals(PhaseId.RENDER_RESPONSE) && !xId.equals(PhaseId.INVOKE_APPLICATION)) || pEvent.getRowIndex() != -1){
//			}

			if (xId == null 
//			 || (!xId.equals(PhaseId.RENDER_RESPONSE) && !xId.equals(PhaseId.INVOKE_APPLICATION)) || pEvent.getRowIndex() != -1) {
			 || (xId.equals(PhaseId.INVOKE_APPLICATION) && pEvent.getRowIndex() != -1)){
				try {
					pvCopyValueFromResultDataModel(pEvent.getRowIndex());
				} catch (DBSIOException e) {
					wLogger.error(e);
				}
			}
			
//			if (xId == null 
//			 || ((xId.equals(PhaseId.RENDER_RESPONSE) || xId.equals(PhaseId.INVOKE_APPLICATION)) && pEvent.getRowIndex() != -1)){
//				try {
//					synchronize();
//				} catch (DBSIOException e) {
//					wLogger.error(e);
//				}
//			}
			
//			if (xId == null 
//			 || (pEvent.getRowIndex() != -1 &&
//			    ((xId.equals(PhaseId.INVOKE_APPLICATION) && pEvent.getRowIndex() != -1) || xId.equals(PhaseId.RENDER_RESPONSE)))){
//				setCurrentRowIndex(pEvent.getRowIndex()); 
//			}else{
//				System.out.println("stop");
//			}
		}
	};

	//private boolean				wHasVersionControl=false;

	//######################################################################################################### 
	//## Public Properties                                                                                    #
	//#########################################################################################################
    /**
     * Cria novo DAO.
     * @param pConnection Conexão com o banco de dados
     */
    public DBSDAO(Connection pConnection) {
    	this.setConnection(pConnection); 
    }
    
    /**
     * Cria novo DAO.
     * @param pDataModel Classe Model da tabela do banco de dados ou classe com atributos homônimos as colunas com as quais se deseje trabalhar no DAO.<br/>
     * @param pConnection
     * @throws DBSIOException 
     */
    public DBSDAO(Class<DataModelClass> pDataModelClass, Connection pConnection) throws DBSIOException {
    	super(pDataModelClass);
    	this.setConnection(pConnection);
    	//Recupera nome da tabela, através da anotação @DataModel, caso exista na classe informada
    	DBSTableModel xAnnotation = DBSIO.getAnnotationDataModel(wDataModel);
    	if (xAnnotation!=null){
    		this.setCommandTableName(xAnnotation.tablename());
    	}

    }
    
    /**
     * Cria novo DAO.
     * @param pConnection Conexão com o banco de dados
     * @param pCommandTableName Nome da tabela que sofrerá Insert/Update/Delete.<br/> 
     *        O nome deverá ser o nome exato que está no banco de dados.<br/> 
     *        Certifique-se que não há problema de letra maiúscula ou minúscula para encontrar a tabela no banco.<br/>
     *        Caso também esteja sendo executada uma <i>query</i> via <b>open</b>, está tabela <b>não</b> poderá ter um <i>alias</i>. 
     * @throws SQLException 
     */
    public DBSDAO(Connection pConnection, String pCommandTableName) throws DBSIOException{
    	this.setConnection(pConnection);
    	this.setCommandTableName(pCommandTableName);
    } 

    /**
     * Cria novo DAO.
     * @param pDataModelClass Classe Model da tabela do banco de dados ou classe com atributos homônimos as colunas com as quais se deseje trabalhar no DAO.
     * @param pConnection Conexão com o banco de dados.
     * @param pCommandTableName Nome da tabela que sofrerá Insert/Update/Delete.<br/> 
     *        Certifique-se que não há problema de letra maiúscula ou minúscula para encontrar a tabela no banco.
     * @throws SQLException 
     */
    public DBSDAO(Class<DataModelClass> pDataModelClass, Connection pConnection, String pCommandTableName) throws DBSIOException{
    	super(pDataModelClass);
    	this.setConnection(pConnection);
    	this.setCommandTableName(pCommandTableName);
    } 

    /**
     * Cria novo DAO.
     * @param pConnection Conexão com o banco de dados.
     * @param pCommandTableName Nome da tabela que sofrerá Insert/Update/Delete.<br/> 
     *        Certifique-se que não há problema de letra maiúscula ou minúscula para encontrar a tabela no banco.
     * @param pPK Nomes das colunas que serão utilizadas para identificar se é um registro único separadas por virgula(,).<br/> 
     *        Esta informação precisa ser passada caso as colunas que são PK não estejam configuradas no banco de dados como tal.
     * @throws SQLException 
     */
    public DBSDAO(Connection pConnection, String pCommandTableName, String pPK) throws DBSIOException{
    	this.setConnection(pConnection);
    	this.setCommandTableName(pCommandTableName, pPK);
    } 

    /**
     * Cria novo DAO.
     * @param pDataModelClass Classe Model da tabela do banco de dados ou classe com atributos homônimos as colunas com as quais se deseje trabalhar no DAO.
     * @param pConnection Conexão com o banco de dados.
     * @param pCommandTableName Nome da tabela que sofrerá Insert/Update/Delete.<br/>
     *        Certifique-se que não há problema de letra maiúscula ou minúscula para encontrar a tabela no banco.
     * @param pPK Nomes das colunas que serão utilizadas para identificar se é um registro único, separadas por virgula(,).
     *        Esta informação precisa ser passada caso as colunas que são PK não estejam configuradas no banco de dados como tal.
     * @throws SQLException 
     */
    public DBSDAO(Class<DataModelClass> pDataModelClass, Connection pConnection, String pCommandTableName, String pPK) throws DBSIOException{
    	super(pDataModelClass);
    	this.setConnection(pConnection);
    	this.setCommandTableName(pCommandTableName, pPK);
    } 


	/**
	 * Retorna a Conexão.
	 * @param pConnection Conexão com o banco de dados
	 */
	public final void setConnection(Connection pConnection) {
		if (!DBSObject.isEmpty(pConnection)){
			this.wConnection = pConnection;
		}
	}

	/**
	 * Configura a Conexão.
	 * @return Conexão com o banco  
	 */
	public final Connection getConnection() {
		return wConnection;
	}

	/**
	 * Retorna comando SQL utilizado para efetuar a pesquisa.
	 * @return Comando Sql utilizado
	 */
	public final String getQuerySQL() {
		return wQuerySQL;
	}
	
	/**
	 * Configura o comando SQL utilizado para pesquisa.<br/>
	 * @param pQuerySQL
	 */
	public final void setQuerySQL(String pQuerySQL) {
		wQuerySQL = pQuerySQL;
	}
	

	/**
	 * @return Quantidade de colunas da pesquisa.
	 * @throws SQLException 
	 */
	public final int getQueryColumnsCount() throws SQLException{
		return wQueryResultSetMetaData.getColumnCount(); 
	}

	/**
	 * Retorna o total de linhas da Query no caso de ter efetuado uma pesquisa via <b>open</b>.<br/>
	 * Retorna o total de linhas da tabela no caso de <b>não</b> ter efetuado uma pesquisa via <b>open</b>, mas ter definido a <b>CommandTableName</b>.
	 * @return Quantidade.
	 * @throws DBSIOException 
	 */
	@Override
	public final int getRowsCount() throws DBSIOException{
		if (wResultDataModel == null){
			if (DBSObject.isEmpty(this.wQuerySQL) 
			&& !DBSObject.isEmpty(this.wCommandTableName)){
				return DBSIO.getTableRowsCount(this.wConnection, this.wCommandTableName);
			}
			return 0;
		}else{
			return wResultDataModel.getRowCount();
		}
//		Comentado em 3/dez/2013 - Aparentemente, código abaixo era desnecessário. - Ricardo
//		try {
//			if (!DBSObject.isEmpty(this.wQuerySQL)){
//				return DBSIO.getSQLRowsCount(this.wConnection, this.wQuerySQL);
//			}else if (!DBSObject.isEmpty(this.wCommandTableName)){
//				return DBSIO.getTableRowsCount(this.wConnection, this.wCommandTableName);
//			}else{
//				return 0;
//			}
//		} catch (DBSIOException e) {
//			wLogger.error("getRowsCount", e);
//			return 0;
//		}
	}
	

	
	/**
	 * Retorna a indice do registro corrente.
	 * Caso não haja registro, retorna -1.
	 * @return
	 */
	public final int getCurrentRowIndex(){
		if (getResultDataModel()==null){
			wCurrentRowIndex = -1;
		}
		return wCurrentRowIndex; //getResultDataModel().getRowIndex(); 
	}
	
	/**
	 * Seta o registro corrente a partir o indice informado.<br/>
	 * Caso indice seja maior que o existente, posiciona no último.<br/>
	 * Caso não existam registros, posiciona no 'anterior ao primeiro'(-1).
	 * @param pRowIndex
	 * @throws DBSIOException 
	 */
	public final boolean setCurrentRowIndex(int pRowIndex) throws DBSIOException{
		if (getResultDataModel()==null){
			return false;
		}
		boolean xOk = true;
		//Posiciona no último registro caso o valor informado seja maior a quantidade de registros existentes
		if ((pRowIndex + 1) > getResultDataModel().getRowCount()){
			pRowIndex = getResultDataModel().getRowCount() - 1;
			xOk = false;
		}
		//Posiciona no registro anterior ao primeiro caso o valor informado seja inferior a -1
		if (pRowIndex < -1){
			pRowIndex = - 1;
			xOk = false;
		}
		//Indica qual o registro corrente a partir o indice informado.
		//Este comando dispara automativamente o evento rowSelected, que por sua vez chama pvSetRowPositionChanged, 
		//onde os valores são recupedados do resultset e copiados para as variáveis locais
		if (pRowIndex != getResultDataModel().getRowIndex()){
			getResultDataModel().setRowIndex(pRowIndex); 
		}
		
		pvCopyValueFromResultDataModel(pRowIndex);

		return xOk;
	}
	
	
	/**
	 * Retorna se o registro atual é um novo registro.<br/>
	 * Os dados deste registro existem somente em memória, sendo necessário implementar a rotina para salva-los.
	 * @return
	 */
	public final boolean getIsNewRow(){
		if (wResultDataModel == null){
			return false;
		}
		if (wResultDataModel.getRowIndex() > (getRowsCountAfterRefresh() - 1)){
			return true;
		}
		return false;
	}
	
	public boolean isMerging() {
		return wIsMerging;
	}

	public void setMerging(boolean pMerging) {
		wIsMerging = pMerging;
	}

	/**
	 * Nome da coluna na tabela ou alias(as) atribuido no select.
	 * @param pColumnIndex númeroda coluna que se deseja saber o nome
	 * @return Nome da coluna
	 * @throws SQLException 
	 */
	public final String getQueryColumnName(Integer pColumnIndex) throws SQLException{
		return wQueryResultSetMetaData.getColumnName(pColumnIndex);
	}	
	

	/**
	 * Retorna todas as colunas da query.
	 * @return
	 */
	@Override
	public final Collection<DBSColumn> getColumns() {
		return wQueryColumns.getColumns();
	}

	/**
	 * Retorna coluna a partir do nome informado.<br/>
	 * Caso a coluna não exista na query, 
	 * será pesquisado também na tabela principal que sofrerá a edição(se houver).
	 * @param pColumnName
	 * @return
	 */
	@Override
	public final DBSColumn getColumn(String pColumnName) {
		if (pColumnName==null){return null;}
		String xColumnName = pvGetColumnName(pColumnName);
		if (wQueryColumns.containsKey(xColumnName)){
			return wQueryColumns.getColumn(xColumnName);
		}else if (wCommandColumns.containsKey(xColumnName)){
			return wCommandColumns.getColumn(xColumnName);
		}
		wLogger.error("getValue:Coluna não encontrada.[" + pColumnName + "][" + wQuerySQL + "][" + wCommandTableName + "]");
		return null;
	}

	
	
	/**
	 * retorna a coluna a partir no número informado
	 * @return
	 */
	@Override
	public final DBSColumn getColumn(int pColumnIndex) {
		return wQueryColumns.getColumn(pColumnIndex);
	}
	
	/**
	 * @return Todas as colunas da tabela que sofrerá modificação.
	 */
	public final Collection<DBSColumn> getCommandColumns() {
		return wCommandColumns.getColumns();
	}

	/**
	 * Retorna coluna da tabela.
	 * @param pColumnName Nome da coluna que se deseja os dados
	 * @return Coluna ou null se não for encontrada
	 */
	public final DBSColumn getCommandColumn(String pColumnName){
		if (pColumnName==null){return null;}
		pColumnName = pvGetColumnName(pColumnName);
		return wCommandColumns.getColumn(pvGetColumnName(pColumnName));
	}	
	

	/**
	 * @return Noma da tabela que sofrerá a modificação
	 */
	public final String getCommandTableName() {
		return wCommandTableName;
	}


	/**
	 * Configura o nome da tabela que sofrerá as modificações(<b>INSERT, UPDATE, DELETE</b>).<br/>
	 * Não é necessário efetuar uma <i>query</i> via <i>Open</i> para efetuar as modificações. 
	 * No entando, os dados recuperados pela query, serão aproveitados para efeito das modificações,
	 * facilitando, por exemplo, um <b>Update</b> no registro corrente. 
	 * @param pCommandTableName Nome da tabela.
	 * @throws SQLException 
	 */
	public final void setCommandTableName(String pCommandTableName) throws DBSIOException{
		setCommandTableName(pCommandTableName, "");
	}

	/**
	 * Configura o nome da tabela que sofrerá as modificações.<br/>
	 * Não é necessário efetuar uma <i>query</i> via <i>Open</i> para efetuar as modificações. 
	 * No entando, os dados recuperados pela query, serão aproveitados para efeito das modificações,
	 * facilitando, por exemplo, um <b>Update</b> no registro corrente. 
	 * @param pCommandTableName Nome da tabela que sofrerá a modificação
	 * @param pPK Nomes da colunas que vão representar a chave primária, caso queira forçar ou definir uma PK, mesmo que não exista na tabela.
	 * @throws DBSIOException
	 */
	public final void setCommandTableName(String pCommandTableName, String pPK) throws DBSIOException{
		if (!DBSObject.isEmpty(pCommandTableName) && //Se nome não for vazio 
			!wCommandTableName.equals(pCommandTableName.trim())){//Se nome da table for diferente da anterior
			if (wQueryColumns.size() > 0){
				wLogger.error("DBSDAO: CommandTableName deve ser configurada ANTES de efetuar o Open() ou no momento da criação da nova instância do DAO.");
			}
			this.wCommandTableName = pCommandTableName.trim();
			pvSetPK(pPK);
			pvCreateCommandColumns();
		}
	}

	/**
	 * Retorna nome das colunas que identificam a chave primária dos resgistros 
	 * da tabela pricipal que sofrerá a edição, conforme definição da <b>commandTableName</b>.<br/>
	 * No caso de haver mais de uma coluna como PK, os nomes das colunas serão separados por vírgula.
	 * @return
	 */
	public final String getPK(){
		return wPK;
	}

	/**
	 * Retorna uma string contendo os nomes das colunas que formam o UK que será responsável 
	 * por identificar um linha única, podente haver colunas de mais de uma tabela ou <b>alias</b>.<br/>
	 * No caso de haver mais de uma coluna como UK, os nomes das colunas serão separados por vírgula.
	 * @return
	 */
	@Override
	public final String getUK(){
		return wUK;
	}
	
	/**
	 * Retorna valor da UK assumunindo que há somente uma coluna.<br/>
	 * Coluna pode ser um <b>alias</b> de mais de uma coluna.
	 * @return
	 */
	@Override
	public final Object getUKValue(){
		return this.getValue(UKName);
	}

	/**
	 * Indica se a coluna que é PK é de auto-incremento. O padrão é TRUE.
	 * Se a tabela possuir mais de uma coluna como PK, o padrão passa a ser FALSE.
	 * @return
	 */
	public final boolean isAutoIncrementPK() {
		return wAutoIncrementPK;
	}

	/**
	 * Indica se a coluna que é PK é de auto-incremento.
	 * @param pAutoIncrementPK
	 */
	public final void setAutoIncrementPK(boolean pAutoIncrementPK) {
		this.wAutoIncrementPK = pAutoIncrementPK;
	}	



	/**
	 * Retorna o valor da coluna antes de alteração
	 * @param pColumnName
	 * @return
	 */
	public final <A> A getValueOriginal(String pColumnName){
		if (pColumnName==null){return null;}
		String xColumnName = pvGetColumnName(pColumnName);
		
		if (wCommandColumns.containsKey(xColumnName)){
			return wCommandColumns.<A>getValueOriginal(xColumnName);
		}else if (wQueryColumns.containsKey(xColumnName)){
			return wQueryColumns.<A>getValueOriginal(xColumnName);
		}
		wLogger.error("DBSDAO.getValue:Coluna não encontrada.[" + pColumnName + "][" + wQuerySQL + "]");
		return null;
	}
	
	
//	public static <A> A getValueX(String pColumnName){
//		Double x = 0D;
//		return (A) x;
//	}
//	
	/**
	 * Retorna valor da coluna
	 * @param pColumnName Nome da coluna
	 * @return Valor 
	 */
	@Override
	public final <A> A getValue(String pColumnName){
		if (pColumnName==null){return null;}
		String xColumnName = pvGetColumnName(pColumnName);
		
		//Retorna valor do DataModel se existir
		if (wDataModel != null){
			A xValue = pvGetLocalDataModelValue(xColumnName); 
			//Retorna valor se não for nulo.  
			if (xValue != null){
				return xValue;
			}else{
				//Verifica se o campo existe no dataModel. Se não existir, tentará encontrar a coluna no wCommandColumn ou wSelectColumn abaixo
				Field xField = DBSIO.getDataModelField(wDataModel, xColumnName);
				if (xField!=null){
					return null;
				}
			}
		}
		//Retorna valor a parti do controle local das colunas de comando
		if (wCommandColumns.containsKey(xColumnName)){
			return wCommandColumns.<A>getValue(xColumnName);
			//Retorna valor a parti do controle local das colunas de pesquisa
		}else if (wQueryColumns.containsKey(xColumnName)){
			return wQueryColumns.<A>getValue(xColumnName);
		}
		wLogger.error("DBSDAO.getValue:Coluna não encontrada.[" + pColumnName + "][" + wQuerySQL + "]");
		return null;
	}
	
	/**
	 * Retorna o valor da coluna convertida para a classe do tipo informado
	 * @param pColumnName Nome da coluna
	 * @param pValueClass Classe para a qual será convertido o valor recebido
	 * @return
	 */	
	public final <A> A getValue(String pColumnName, Class<A> pValueClass){
		return DBSObject.<A>toClass(getValue(pColumnName), pValueClass);
	}

	@Override
	public final void setValue(String pColumnName, Object pValue){
		this.setValue(pColumnName, pValue, false); 
	}
	

	@Override
	public final void setValue(String pColumnName, Object pValue, boolean pOriginalValue){
		if (pColumnName==null){return;}
		boolean xAchou = false;
		String xColumnName = pvGetColumnName(pColumnName);

		//Seta o valor na coluna da tabela que poderá sofrer alteração
		if (wCommandColumns.containsKey(xColumnName)){
			wCommandColumns.setValue(xColumnName, pValue, pOriginalValue);
			xAchou = true;
		}
		//Seta o valor na coluna do select, independentemente de ser uma coluna que da tabela comando(CommandTableName)
		if (wQueryColumns.containsKey(xColumnName)){
			wQueryColumns.setValue(xColumnName, pValue, pOriginalValue);
			xAchou = true;
		}
		if (this.pvSetLocalDataModelValue(xColumnName, pValue)){
			xAchou = true;
		}
		if (!xAchou){
			wLogger.error("DBSDAO.setValue:Coluna não encontrada.[" + pColumnName + "][" + wQuerySQL + "]");
		}
	}
	
	/**
	 * Retorna valor da coluna diretamente do ResultDataModel.<br/>
	 * Isto é utilizado, durante a atualização de um dataTable, 
	 * os dados do registro atual somente poderam ser recuperado por este método.
	 * @param pColumnName
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public final <A> A getListValue(String pColumnName){
		if (wResultDataModel != null){
			String xColumnName = pvGetColumnName(pColumnName);
			return (A) wResultDataModel.getRowData().get(xColumnName); 
		}else{
			return null;
		}
	}
	
	/**
	 * Retorna valor da coluna informada diretamente do ResultDataModel convertida para a classe do tipo informado.
	 * @param pColumnName Nome da coluna
	 * @param pValueClass Classe para a qual será convertido o valor recebido
	 * @return
	 */	
	public final <A> A getListValue(String pColumnName, Class<A> pValueClass){
		return DBSObject.<A>toClass(getListValue(pColumnName), pValueClass);
	}
	
	/**
	 * Seta o valor da coluna informada diretamenteo no ResultDataModel.
	 * @param pColumnName Nome da coluna
	 * @param pValue Valor da coluna
	 * @return
	 */	
	public final void setListValue(String pColumnName, Object pValue){
		
		if (wResultDataModel != null){
			String xColumnName = pvGetColumnName(pColumnName);
			wResultDataModel.getRowData().put(xColumnName, pValue); 
		}
	}
	
	/**
	 * Retorna o comando que será executado no INSERT, UPDATE, DELETE, SELECT
	 * @param pCommand Comando que será executado
	 * @return Retorna a String contendo o comando SQL que será executado
	 * @throws DBSIOException 
	 */
	public final String getSQLExecuteCommand(DBSDAO.COMMAND pCommand) throws DBSIOException{
		String xStr = "";
		pvCopyDataModelFieldsValueToCommandValue(wDataModel);
		xStr = DBSIO.getDAOSQLCommand(this, pCommand);
		return xStr;
	}
	/**
	 * Use este atributo para popular páginas pelos ManagedBeans.
	 * Retorna os registro de forma que possam ser entendidos nas páginas xhtml por EL.
	 * As colunas poderão ser acessadas diretamente como atributos de uma classe.<br/>  
	 * Os nomes dos atributos são os próprios nomes definidos as colunas do select.<br/>
	 * exemplo de código xhtlm "#{table.campo}"
	 * @return
	 */
	public final DBSResultDataModel getResultDataModel(){
		return wResultDataModel;
	}
	/**
	 * Verifica se coluna existe conforme o nome informado
	 * @param pColumnName
	 * @return true = Existe / false = não existe
	 */
	public final boolean containsColumn(String pColumnName){
		if (pColumnName==null){return false;}
		String xColumnName = pvGetColumnName(pColumnName);
		if (wCommandColumns.containsKey(xColumnName)){
			return true;
		}else if (wQueryColumns.containsKey(xColumnName)){
			return true;
		}
		return false;
	}

	/**
	 * Executa a query informada em setQuerySQL() ou a executada anteriormente, caso exista.<br/> 
	 * Caso deseje somente atualizar os dados da query, utilize o método <b>refresh()<b>.<br/>
	 * O cursor é posicionado no primeiro registro, sendo necessário executar moveBeforeFirst quando se deseja utilizar while(dao.movenext) 
	 * @throws DBSIOException 
	 */
	@Override
	public final boolean open() throws DBSIOException{
		return this.open(wQuerySQL, getUK());
	}


	/**
	 * Executa a query informada em <b>pQuerySQL</b>. 
	 * O cursor é posicionado no primeiro registro, sendo necessário executar moveBeforeFirst quando se deseja utilizar while(dao.movenext) 
	 * @param pQuerySQL Select SQL que será utlizada para efetuar a pesquisa
	 * @throws DBSIOException 
	 */
	@Override
	public final boolean open(String pQuerySQL) throws DBSIOException{
		return this.open(pQuerySQL,"");
	}

	/**
	 * Executa a query informada em <b>pQuerySQL</b>.
	 * O cursor é posicionado no primeiro registro, sendo necessário executar moveBeforeFirst quando se deseja utilizar while(dao.movenext) 
	 * @param pQuerySQL Query padrão SQL ANSI que será utlizada para efetuar a pesquisa
	 * @param pUK String com os nomes das columnas(separadas por virgula) que será utilizadas como UK dos registros,
	 *        em substituição a PK da tabela, caso exista ou tenha sido informada no construtor do DAO.
	 * @return true = Sem erro / false = erro / null = exception
	 * @throws DBSIOException 
	 */
	public final synchronized boolean open(String pQuerySQL, String pUK) throws DBSIOException{
		//System.out.println("DBSDO - open INICIO-----------------------------");
		if (wConnection == null){
			wLogger.error("DBSDAO:open: Conexão não informada.");
			return false;
		}
		if (DBSObject.isEmpty(pQuerySQL)){
			wResultDataModel = null;
			wQueryResultSetMetaData = null;
			return false;
		}


		//Se não for uma pesquisa com o comando 'SELECT', ignora a chamada
		if (DBSString.getInStr(pQuerySQL, "SELECT ",false)==0){
			return false;
		}

		wQuerySQLUK = pQuerySQL.trim();
		wQuerySQL = wQuerySQLUK;
		
		if (DBSObject.isEmpty(pUK)){ //Configura a UK dos registros se o conteúdo não for vazio
			//Configura a UK como sendo a PK tabela, caso a pesquisa seja de uma única tabela
			if (DBSString.getStringCount(wQuerySQLUK, "Select", false) <= 1){
				pvSetUK(wPK);  
			}else{
				//Sendo uma pesquisa de mais de uma tabela, não utliza a PK como UK, passando a ser necessário que
				//o usuário informe a UK caso queira identificar a posição do registro
				pvSetUK("");
			}
		}else{
			//Utiliza a UK informada;
			pvSetUK(pUK);
		}
		
		//Se foi definido as columas de UK da pesquisa. Cria uma coluna de conterá a UK que será utilizada para identificar a linha
		if (wUKs.length>0){
			wQuerySQLUK = DBSIO.changeAsteriskFromQuerySQL(wQuerySQLUK);
			if (DBSString.getInStr(wQuerySQLUK, " DISTINCT ", false) >0){
				//Altera o(s) SELECT DISTINCT
				wQuerySQLUK = DBSString.changeStr(wQuerySQLUK, "SELECT DISTINCT", "SELECT DISTINCT " + this.pvGetUKConcatenaded() + " AS " + UKName + ", ", false);
			}else{
				wQuerySQLUK = DBSString.changeStr(wQuerySQLUK, "SELECT ", "SELECT " + this.pvGetUKConcatenaded() + " AS " + UKName + ", ", false);
				if (DBSString.getInStr(wQuerySQLUK, " UNION ", false) >0){
					wQuerySQLUK = DBSString.changeStr(wQuerySQLUK, "UNION SELECT ", "UNION SELECT " + this.pvGetUKConcatenaded() + " AS " + UKName + ", ", false);
				}
			}
			//Inclui coluna DBSUK no GROUP BY se houver
			wQuerySQLUK = DBSString.changeStr(wQuerySQLUK, "GROUP BY ", "GROUP BY " + this.pvGetUKConcatenaded() + ", ", false);
		}

		//Atualiza a pesquisa
		refresh();

		return true;
	}

	/**
	 * Le os registros da tabela de comando(CommandTable) utilizando como filtro os valores das colunas definidas como chaves.<br/> 
	 * Se não houver definição das colunas que são chaves, serão lidos todos os registros.<br/>
	 * A definição das colunas que são chaves é efetuada manualmente no construtor do DAO ou é recuperada automaticamente
	 * diretamente da definição da tabela no banco de dados. 
	 * Também é possível indicar se a coluna é chave através do atributo <b>getCommandColumn("coluna").setPK(true).<b/>
	 * O parametro <b>pAdditionalSQLWhereCondition</b> é um filtro adicional.<br/>
	 * Este método é similar ao <b>open<b/>, porém a query SQL é criada automaticamente. 
	 * Posiciona no primeiro registro lido de houver.<br/>
	 * Se não houver registro, retorna false.    
	 * @param pAdditionalSQLWhereCondition Texto da condição(sem 'WHERE') a ser adicionada a cláusula 'WHERE' já gerada automaticamente. <br/>
	 * @return false se não encontrar nenhum registro
	 * @throws SQLException 
	 */
	public synchronized boolean openCommandTable(String pAdditionalSQLWhereCondition) throws DBSIOException{
		if (wCommandColumns.size() == 0){
			wLogger.error("DBSDAO:executeUpdate: Não foram encontradas colunas alteradas para efetuar o comando de UPDATE.");
			return false;
		}
		if (this.wConnection!=null){
			String xSQLCommand = DBSIO.getDAOSQLCommand(this, COMMAND.SELECT, pAdditionalSQLWhereCondition);
			open(xSQLCommand); 
			if (getRowsCount() > 0){ 
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Le os registros da tabela de comando(CommandTable) utilizando como filtro os valores das colunas definidas como chaves.<br/> 
	 * Se não houver definição das colunas que são chaves, serão lidos todos os registros.<br/>
	 * A definição das colunas que são chaves é efetuada manualmente no construtor do DAO ou é recuperada automaticamente
	 * diretamente da definição da tabela no banco de dados. 
	 * Também é possível indicar se a coluna é chave através do atributo <b>getCommandColumn("coluna").setPK(true).<b/>
	 * Este método é similar ao <b>open<b/>, porém a query SQL é criada automaticamente. 
	 * Posiciona no primeiro registro lido de houver.<br/>
	 * Se não houver registro, retorna false.    
	 * @return false se não encontrar nenhum registro
	 * @throws SQLException 
	 */
	public synchronized boolean openCommandTable() throws DBSIOException{
		return openCommandTable("");
	}
	/**
	 * Fecha o DAO.
	 * É importante efetuar o close, caso se deseje utilizar o mesmo DAO para efetuar outras acessos.<br/>
	 * Um novo open em um DAO já fechado, irá executar a mesma query já informada anteriormente.
	 * @throws DBSIOException
	 */
	@Override
	public final synchronized  void close() throws DBSIOException{
		if (pvFireEventBeforeClose()){
			//Limpa lista de colunas do query para forçar a recriação em caso de novo open
			wQueryColumns.clear();
			wResultDataModel = null;
			pvFireEventAfterClose(true);
		}
	}
	
	/**
	 * Popula o resultset com os registros atuais e cria lista.
	 * @return true = Sem erro; false = Com erro
	 * @throws SQLException 
	 */
	@SuppressWarnings("unchecked")
	public final synchronized boolean refresh() throws DBSIOException{
		//Executa a Select para recuperar os registros
		if (pvFireEventBeforeOpen()){
			ResultSet xSelectResultSet;
			//Reset dos componentes
			wCurrentRowIndex = -1;
			wResultDataModel = null;
			wQueryResultSetMetaData = null;
			//-----------------
			xSelectResultSet = DBSIO.openResultSet(this.getConnection(),wQuerySQLUK);
			//wResultDataModel é necessário para consulta com html pois possibilita o acesso as colunas do registro
			Result xResult = ResultSupport.toResult(xSelectResultSet);
			wResultDataModel = new DBSResultDataModel(xResult.getRows());
			xResult = null;
			//Configura listener local para acomponhar seleção de registro
			wResultDataModel.addDataModelListener(wDataModelListener);
			try{
				wQueryResultSetMetaData = xSelectResultSet.getMetaData();
				pvCreateSelectColumns(wQueryResultSetMetaData);
				//Chame evento
				pvFireEventAfterOpen(true);
				//Caso não exista o primeiro registro, move para posição inicial onde não há registro válido...
				if (!moveFirstRow()) {
					moveBeforeFirstRow();
				}
				setRowsCountAfterRefresh(getRowsCount());
				return true;
			}catch(SQLException e){
				wLogger.error(e);
				//Chame evento
				pvFireEventAfterOpen(false);
				DBSIO.throwIOException("refreshResultSet:" + wQuerySQLUK, e, wConnection);
				return false;
			}finally{
				DBSIO.closeResultSet(xSelectResultSet);
			}
		}else{
			//Chame evento
			pvFireEventAfterOpen(false);
			return false;
		}
	}
	
	/**
	 * Atualizar os valores correntes com os dados da posição corrente do ResultDataModel.<br/>
	 * @throws DBSIOException
	 */
	public void synchronize() throws DBSIOException{
		if (getResultDataModel() != null){
			pvCopyValueFromResultDataModel(getResultDataModel().getRowIndex());
		}
	}

	
	/**
	 * Retorna se está no primeiro registro
	 * @return
	 */
	public boolean getIsFist(){
		if (wResultDataModel != null){
			return wCurrentRowIndex==0;
		}else{
			return false;
		}
	}
	/**
	 * Retorna se está no último registro
	 * @return
	 */
	public boolean getIsLast(){
		if (wResultDataModel != null){
			return wCurrentRowIndex == (wResultDataModel.getRowCount() - 1); 
		}else{
			return false;
		}
	}
	//######################################################################################################### 
	//## Public Methods                                                                                       #
	//#########################################################################################################
	@Override
	public synchronized void moveBeforeFirstRow() throws DBSIOException{
		pvMove(MOVE_DIRECTION.BEFORE_FIRST);
	}
	
	@Override
	public synchronized boolean moveFirstRow() throws DBSIOException{
		return pvMove(MOVE_DIRECTION.FIRST);
	}
	
	@Override
	public synchronized boolean movePreviousRow() throws DBSIOException{
		return pvMove(MOVE_DIRECTION.PREVIOUS);
	}	
	
	@Override
	public synchronized boolean moveNextRow() throws DBSIOException{
		return pvMove(MOVE_DIRECTION.NEXT);
	}
	

	@Override
	public synchronized boolean moveLastRow() throws DBSIOException{
		return pvMove(MOVE_DIRECTION.LAST);
	}

	/**
	 * Executa o insert da tabela definida como CommandTable.<br/>
	 * Consulte o atributo <b>executeOnlyChangedValues</b> para outras considerações relacionadas ao insert.
	 * @returnQuantidade de linhas afetadas
	 * @throws SQLException
	 */
	@Override
	public synchronized int executeInsert() throws DBSIOException{
		if (wCommandColumns.size() == 0){
			//Mensagem alterada pois deveria ser de Colunas não encontradas e não de tabela
			wLogger.error("DBSDAO:executeUpdate: Não foram encontradas colunas alteradas para efetuar o comando de UPDATE.");
			return 0;
		}
		int xCount = 0;
		if (this.wConnection!=null){
			pvCopyDataModelFieldsValueToCommandValue(wDataModel);
			if (pvFireEventBeforeInsert()){
				xCount = DBSIO.executeDAOCommand(this, DBSDAO.COMMAND.INSERT, wAutoIncrementPK);
				pvFireEventAfterInsert(true);
				return xCount;
			}
		}
		pvFireEventAfterInsert(false);
		return xCount;
	}

	
	/**
	 * Exclui e retorna a quantidade de registros excluidos.<br/>
	 * @return Quantidade de linhas afetadas
	 * @throws SQLException
	 */
	@Override
	public synchronized final int executeDelete() throws DBSIOException{
		if (wCommandColumns.size() == 0){
			//Mensagem alterada pois deveria ser de Colunas não encontradas e não de tabela
			wLogger.error("DBSDAO:executeUpdate: Não foram encontradas colunas alteradas para efetuar o comando de UPDATE.");
//			wLogger.error("DBSDAO:executeDelete: Não foi informada a tabela para efetuar os comandos. Utilize setCommandTableName ou informe no construtor.");
			return 0;
		}
		int xCount = 0;
		if (this.wConnection!=null){
			if(pvFireEventBeforeDelete()){
				pvCopyDataModelFieldsValueToCommandValue(wDataModel);
				xCount = DBSIO.executeDAOCommand(this, DBSDAO.COMMAND.DELETE);
				pvFireEventAfterDelete(true);
				return xCount; 
			}
		}
		pvFireEventAfterDelete(false);
		return xCount;
	}

	/**
	 * Atualiza registro.<br/>
	 * Consulte o atributo <b>executeOnlyChangedValues</b> para outras considerações relacionadas ao update.<br/>
	 * Colunas definidas como PK não serão atualizadas.
	 * @return Quantidade de linhas afetadas
	 * @throws SQLException 
	 */
	@Override
	public synchronized int executeUpdate() throws DBSIOException{
		return executeUpdate("");
	}

	/**
	 * Atualiza registros  
	 * @param pAdditionalSQLWhereCondition Texto da condição(sem 'WHERE') a ser adicionada a cláusula 'WHERE' que já será gerada automaticamente. <br/>
	 * Colunas definidas como PK não serão atualizadas.
	 * @return Quantidade de linhas afetadas
	 * @throws SQLException 
	 */
	public synchronized int executeUpdate(String pAdditionalSQLWhereCondition) throws DBSIOException{
		if (wCommandColumns.size() == 0){
			//Mensagem alterada pois deveria ser de Colunas não encontradas e não de tabela
			wLogger.error("DBSDAO:executeUpdate: Não foram encontradas colunas alteradas para efetuar o comando de UPDATE.");
			return 0;
		}
		int xCount = 0;
		if (this.wConnection!=null){
			//Chama evento
			if (pvFireEventBeforeUpdate()){
				//Copia os valores do wDataModel para os valores das colunas que efetuará o comando
				pvCopyDataModelFieldsValueToCommandValue(wDataModel); 
				//Executa o update
				xCount = DBSIO.executeDAOCommand(this, COMMAND.UPDATE, pAdditionalSQLWhereCondition);
				pvFireEventAfterUpdate(true);
				return xCount; 
			}
		}
		pvFireEventAfterUpdate(false);
		return 0;
	}
	
	@Override
	/**
	 * Efetua o <b>UPDATE</b> considerando a PK, e caso não tenha sido encontrado registro algum, efetua o <b>INSERT</b>.<br/>
	 * Isto otimiza a utilização do espaço do banco de dados em comparação um <b>DELETE</b> seguido de <b>INSERT</b>.<br/>
	 * Porém aumenta o tempo de processamento, já que será efetuada a tentativa de <b>UPDATE</b> antes do <b>INSERT</b>.<br/>
	 * Colunas definidas como PK não serão atualizadas.
	 * @return Quantidade de linhas afetadas
	 * @throws SQLException 
	 */
	public synchronized final int executeMerge() throws DBSIOException{
		return executeMerge("");
	}
	
	/**
	 * Efetua o <b>UPDATE</b> considerando a PK, e caso não tenha sido encontrado registro algum, efetua o <b>INSERT</b>.<br/>
	 * Isto otimiza a utilização do espaço do banco de dados em comparação um <b>DELETE</b> seguido de <b>INSERT</b>.<br/>
	 * Porém aumenta o tempo de processamento, já que será efetuada a tentativa de <b>UPDATE</b> antes do <b>INSERT</b>.<br/>
	 * Colunas definidas como PK não serão atualizadas.
	 * @param pAdditionalSQLWhereCondition Texto da condição(sem 'WHERE') a ser adicionada a cláusula 'WHERE' que já será gerada automaticamente no caso do <b>UPDATE</b>.<br/>
	 * @return Quantidade de linhas afetadas
	 * @throws SQLException 
	 */
	public synchronized final int executeMerge(String pAdditionalSQLWhereCondition) throws DBSIOException{
		if (wCommandColumns.size() == 0){
			//Mensagem alterada pois deveria ser de Colunas não encontradas e não de tabela
			wLogger.error("DBSDAO:executeUpdate: Não foram encontradas colunas alteradas para efetuar o comando de UPDATE.");
//			wLogger.error("DBSDAO:executeMerge: Não foi informada a tabela para efetuar os comandos. Utilize setCommandTableName ou informe no construtor.");
			return 0;
		}
		int xN=-1;
		if (this.wConnection!=null){
			pvCopyDataModelFieldsValueToCommandValue(wDataModel);
			if (pvFireEventBeforeMerge()){
					//Savepoint xS  = DBSIO.beginTrans(this.getConnection(), "EXECUTEMERGE"); //Cria savepoint interno para retornar em caso de erro já que o update pode funcionar mais o insert não
				try{
					setMerging(true);
					xN = executeUpdate(pAdditionalSQLWhereCondition);//Atualiza registro, se existir
					if (xN==0){ //Se não foi atualiza registro algum...
						xN = executeInsert(); //Insere novo registro
					}
					if (xN<=0){ //Se nehum registro foi alterado é pq houve erro
						//DBSIO.endTrans(this.getConnection(),false,xS); //ignora Update ou Insert em caso de erro. Rollback até EXECUTEMERGE
					}
				}catch(DBSIOException e){
					throw e;
				}finally{
					setMerging(false);
				}
				pvFireEventAfterMerge(true);
				return xN;
			}
		}
		pvFireEventAfterMerge(false);
		return xN;
	}


	/**
	 * Força que os valores atuais sejam os valores lidos originalmente
	 */
	public final void restoreValuesOriginal(){
		wQueryColumns.restoreValuesOriginal();
		wCommandColumns.restoreValuesOriginal();
	}
	
	/**
	 * Força que os valores atuais seja o default
	 */
	public final void restoreValuesDefault(){
		wQueryColumns.restoreValuesDefault();
		wCommandColumns.restoreValuesDefault();
	}

	/**
	 * Força que o valor original seja igual ao atual
	 */
	public final void copyValueToValueOriginal(){
		wQueryColumns.copyValueToValueOriginal();
		wCommandColumns.copyValueToValueOriginal();
	}
	
	/**
	 * Seta o registro corrente como tendo os valores iquais aos indice informado e
	 * prepara todos as colunas para serem enviadas no próximo <b>insert</b> ou <b>update</b>.<br/> 
	 * Após este comando e antes do respectivo <b>insert</b> ou <b>update</b>, é possível alterar os valores pontualmente, 
	 * <b>principalmente setar com nulo os campos que são <b>pk</b>, no caso de um insert</b>
	 * @param pRowIndex
	 * @throws DBSIOException
	 */
	public final void paste(int pRowIndex) throws DBSIOException{
		setCurrentRowIndex(pRowIndex);
		wQueryColumns.setChanged();
		wCommandColumns.setChanged();
	}
	
	/**
	 * Prepara todos as colunas para serem enviadas no próximo <b>insert</b> ou <b>update</b>, 
	 * considerando os valores do registro atual.<br/>
	 * Após este comando e antes do respectivo <b>insert</b> ou <b>update</b>, é possível alterar os valores pontualmente, 
	 * <b>principalmente setar com nulo os campos que são <b>pk</b>, no caso de um insert</b>
	 * @param pRowIndex
	 * @throws DBSIOException
	 */
	public final void paste() throws DBSIOException{
		setCurrentRowIndex(getCurrentRowIndex());
		wQueryColumns.setChanged();
		wCommandColumns.setChanged();
	}

	/**
	 * Inserir linha em branco ao resultaDataModel do DAO.<br/>
	 * Linha é criada somente na memória.
	 * @throws DBSIOException 
	 */
	public final void insertEmptyRow() throws DBSIOException{
		DBSIO.insertEmptyRow(this);
	}

	/**
	 * Cria em memória as colunas da tabela que poderão sofrer inclusão ou alteração.
	 * @throws SQLException 
	 */
	private void pvCreateCommandColumns() throws DBSIOException{
		ResultSet xMetaData = null;
		try{
			
			if (!DBSObject.isEmpty(wCommandTableName)){
				String 	xColumnName;
				boolean xEmpty = true;
				//Excluir todas as colunas caso existam
				wCommandColumns.clear(); 
				//TODO inplementar controle de versão
				//this.wHasVersionControl = false; //reseta a informação se há controle de versão durante o comando update
				xMetaData = DBSIO.getTableColumnsMetaData(this.getConnection(), wCommandTableName.toUpperCase());
				//Inclui todas as colunas da tabela de comando no controle de colunas local
				while (DBSIO.moveNext(xMetaData)){
					xEmpty = false;
					xColumnName = xMetaData.getString("COLUMN_NAME").toUpperCase().trim();
					if (!DBSIO.isColumnsIgnored(xColumnName)){
						wCommandColumns.MergeColumn(xColumnName,
												    DBSIO.toDataType(this.getConnection(), xMetaData.getInt("DATA_TYPE"), xMetaData.getInt("COLUMN_SIZE")),
												    xMetaData.getInt("COLUMN_SIZE"), 
												    xMetaData.getObject("COLUMN_DEF"));
						//TODO Controle de versão
						//				if (xMetaData.getString("COLUMN_NAME").toUpperCase().trim().equals(DBSSDK.IO.VERSION_COLUMN_NAME)){
						//					this.wHasVersionControl = true; //seta informando que existe controle de versão do update
						//				}
						//Força a columa como sendo PK conforme a informação passada pelo usuário 
						if (pvIsPK(xColumnName)){
							if (wCommandColumns.containsKey(xColumnName)){
								wCommandColumns.getColumn(xColumnName).setPK(true);
							}else{
								//Se não encontrou a coluna informada, considera que a tabela não possui auto-incremento.
								setAutoIncrementPK(false);
							}
						}
					}
				} 
				if (xEmpty){
					wLogger.error("Não foi encontrada a tabela " + wCommandTableName + ". Verifique o acesso, o nome e questões de letra maiúscula/minúscula.");
				}
				//Se a PK não foi definida, busca definição na própria tabela
				if (wPKs.length==0){
					//Configura quais as colunas são PK
					List<String> xPKs = DBSIO.getPrimaryKeys(this.getConnection(), wCommandTableName);
					String xPK = "";
					if (xPKs != null){
						for (int x=0; x<=xPKs.size()-1;x++){
							wCommandColumns.getColumn(xPKs.get(x)).setPK(true);
							//Define a lista de colunas(wPK) que são PK para serem utilizadas como UK caso o usuario não informe. Isso � imporante para identificar o linha �nica no dataTable
							if (!xPK.equals("")){
								xPK += ",";
								//Se houver mais de uma coluna como PK, considera que a tabela não possui auto-incremento.
								setAutoIncrementPK(false);
							}
							xPK += wCommandColumns.getColumn(xPKs.get(x)).getColumnName();
						}
					}
					pvSetPK(xPK);
				} 
				//Se a PK foi definida e possui mais de uma coluna, considera que a tabela não possui auto-incremento.	
				if (wPKs.length > 1) {
					this.setAutoIncrementPK(false);
				}
			}
		}catch (SQLException e){
			DBSIO.throwIOException(wCommandTableName, e, wConnection);
		}finally{
			DBSIO.closeResultSet(xMetaData);
		}
	}

	/**
	 * Cria, em memória, as colunas a partir das colunas informadas no consulta(wQuerySQL),
	 * para posteriormente serem utilizadas para verificar sua existência.
	 * @throws SQLException 
	 */
	private void pvCreateSelectColumns(ResultSetMetaData pResultSetMetaData) throws DBSIOException{
		try{
			wQueryColumns.clear(); //Excluir todas as colunas caso existam
			//Inclui todas as colunas da pesquina no control de colunas local
			DBSColumn xColumn = null;
			String xColumnName = "";
			for (int x=1; x<=pResultSetMetaData.getColumnCount();x++){
				xColumnName = pResultSetMetaData.getColumnLabel(x).toUpperCase().trim();
				wQueryColumns.MergeColumn(xColumnName,
										   DBSIO.toDataType(this.getConnection(), pResultSetMetaData.getColumnType(x), pResultSetMetaData.getPrecision(x)),
										   pResultSetMetaData.getColumnDisplaySize(x), 
										   null);

				xColumn = wQueryColumns.getColumn(xColumnName);
				//Seta o título padrão do cabeçalho com o mesmo nome da coluna no select(considerando o AS)
				xColumn.setDisplayColumnName(DBSString.toProper(xColumnName));
				//Seta o tamanho  do campo conforme o tamanho da coluna na tabela
				xColumn.setDisplaySize(pResultSetMetaData.getColumnDisplaySize(x));
				//System.out.println(pResultSetMetaData.getColumnLabel(x).toUpperCase().trim() + ":" + pResultSetMetaData.isAutoIncrement(x));
				//Seta coluna como PK caso faça parte da lista de colunas definida na UK informada pelo usuário
				if (pvIsUK(xColumnName)){
					xColumn.setPK(true);
				}
				xColumn.setAutoIncrement(pResultSetMetaData.isAutoIncrement(x));
				//Seta coluna UK criada 
				if (xColumnName.equals(UKName)){
					wQueryColumns.getColumn(UKName).setDisplayColumn(false);
				}
			}
		}catch(SQLException e){
			DBSIO.throwIOException(e, wConnection);
		}
	}


	/**
	 * Salva os valores do ResultSet nas variáveis locais e ativa indicador que existe um novo registro corrente.
	 * @throws DBSIOException
	 */
	private void pvSetRowPositionChanged() throws DBSIOException{
		super.setRowPositionChanged(true);
	}
	
	/**
	 * Salva os valores do ResultSet nas variáveis locais e dispara os eventos beforeRead e afterRead. 
	 * @throws DBSIOException
	 */
	private void pvCopyValueFromResultDataModel(int pRowIndex) throws DBSIOException{
//		if (getResultDataModel()==null){
//			return;
//		}
//		getResultDataModel().setRowIndex(pRowIndex);
		if (pRowIndex != wCurrentRowIndex){
			wCurrentRowIndex = pRowIndex;
			pvSetRowPositionChanged();
			//Atualiza valores atuais e reseta valor original
			pvFireEventBeforeRead();
			if (!wResultDataModel.isRowAvailable()){
				restoreValuesDefault(); 
			}else{
				for (DBSColumn xColumn: wQueryColumns.getColumns()){
					this.setValue(xColumn.getColumnName(), pvGetResultDataModelValueConvertedToDataType(xColumn.getColumnName(), xColumn.getDataType()), true);
				}
			}
			pvFireEventAfterRead(true);
		}
	}
	
	//*****************************************************************************************************
	// PRIVATE
	//*****************************************************************************************************

	
	/**
	 * Retorna valor do resultset convertido para o tipo informado em <b>pDataType</b>
	 * @param pColumnName
	 * @param pDataType
	 * @return
	 * @throws DBSIOException
	 */
	@SuppressWarnings("unchecked")
	private <A> A pvGetResultDataModelValueConvertedToDataType(String pColumnName, DATATYPE pDataType) throws DBSIOException{
		return (A) DBSIO.getDataTypeConvertedValue(pDataType, wResultDataModel.getRowData().get(pColumnName)); //TODO
	}

	private boolean pvMove(MOVE_DIRECTION pDirection) throws DBSIOException {
		boolean xB = false;
		if (wConnection!=null){
			//Força para que o rowindex do resultaset seja o mesmo que foi utilizado para armazenar os dados 
			//Se for para chmara o evento
			xB = pvFireEventBeforeMove();
			int xRowIndex = 0;
			if (xB){
				if (pDirection == MOVE_DIRECTION.BEFORE_FIRST){
					restoreValuesDefault();
				}
				xRowIndex = DBSIO.getIndexAfterMove(getCurrentRowIndex(), getRowsCount(), pDirection);
				if (DBSIO.getIndexAfterMoveIsOk(getCurrentRowIndex(), xRowIndex, getRowsCount(), pDirection)){
					xB = setCurrentRowIndex(xRowIndex);
				}else{
					xB = false;
				}
				pvFireEventAfterMove(xB);
			}
		}
		return xB;
	}	
	
	/**
	 * @return Retorna as colunas utilizadas como UK, já concatenadas.
	 */
	private String pvGetUKConcatenaded(){
		String xS = DBSString.changeStr(wUK, " ", "");
		return DBSString.changeStr(xS, ",", " || ");
	}
	
	/**
	 * Configura a chave que identificará a PK da tabela que sofrerá a edição.<br/>
	 * Caso a chave seja composta por mais de uma coluna, as colunas deverão estar separadas por vírgula.
	 * @param pUK
	 */
	private void pvSetPK(String pPK){
		this.wPK = pvCreatePKString(pPK, this.wCommandTableName); //Incluido nome da tabela na formação da chave /23/07/2013
		this.wPKs = pvCreatePKArray(pPK);
	}
	
	/**
	 * Configura a chave que identificará a UK dos registros, podendo ser composta por colunas de mais de uma tabela ou <b>alias</b>.<br/>
	 * Caso a chave seja composta por mais de uma coluna, as colunas deverão estar separadas por vírgula.
	 * @param pUK
	 */
	private void pvSetUK(String pUK){
		this.wUK = pvCreatePKString(pUK, null);
		this.wUKs = pvCreatePKArray(pUK);
	}

	/**
	 * Cria String da PK a partir da PK informada, padronizando o conteúdo.
	 * @param pPK
	 * @return
	 */
	private String pvCreatePKString(String pPK, String pTableName){
		String xTableAlias = pTableName;
		if (!DBSObject.isEmpty(wQuerySQL) && !DBSObject.isEmpty(xTableAlias)){
			//Retorna o nome da tabela ou alias se existir.
			xTableAlias = DBSIO.getTableFromQuery(wQuerySQL, true, xTableAlias);
		}
		if (DBSObject.isEmpty(pPK)){
			return "";
		}
		String 	 xPK = pPK.trim().toUpperCase();
		String[] xPKs;
		if (pTableName == null){
			pTableName = "";
		}else{
			pTableName = xTableAlias.trim().toUpperCase() + ".";
		}
		xPK = DBSString.changeStr(xPK, ",", " ");
		//Exclui o nome da tabela da chave, caso tenha sido incluida pelo usuário, já que será obrigatóriamente adicionada no código abaixo
		xPK = DBSString.changeStr(xPK, pTableName, "");
		xPKs = xPK.split("\\s+");
		xPK = "";
		for (String xP: xPKs){
			if (!xPK.equals("")){
				xPK = xPK + ",";
			}
			xPK = xPK + pTableName + xP.trim();
		}
		if (xPK.equals("")){
			return xPK;
		}else{
			return xPK.trim();
		}
	}

	/**
	 * Retorna array das chaves(PK/UK) desconsiderando o nome da tabela, se houver.
	 * @param pPK
	 * @return
	 */
	private static String[] pvCreatePKArray(String pPK){
		if (pPK == null ||
			pPK.equals("")){
			return new String[]{};
		}
		String[] xPKs;
		int		 xN;
		xPKs = pPK.split(",");
		for (int xI = 0; xI < xPKs.length; xI++){
			xPKs[xI] = xPKs[xI].trim();
			xN = xPKs[xI].lastIndexOf(".");
			if (xN != -1){
				xPKs[xI] = xPKs[xI].substring(xN+1);
			}
		}
		return xPKs;
	}
	
	/**
	 * Retorna se coluna informada é uma UK.
	 * @param pColumnName
	 * @return
	 */
	private boolean pvIsUK(String pColumnName){
		pColumnName = pColumnName.trim().toUpperCase();
		return (DBSString.findStringInArray(wUKs, pColumnName) > -1);
	}
	
	/**
	 * Retorna se coluna informada é uma PK.
	 * @param pColumnName
	 * @return
	 */
	private boolean pvIsPK(String pColumnName){
		pColumnName = pColumnName.trim().toUpperCase(); 
		return (DBSString.findStringInArray(wPKs, pColumnName) > -1);
	}
	
	/**
	 * Retorna o nome da coluna em caixa alta, trim e sem o nome date tabela, se houver.
	 * @param pColumnName
	 * @return
	 */
	private String pvGetColumnName(String pColumnName){
		if (pColumnName == null){
			return "";
		}
		pColumnName = pColumnName.toUpperCase().trim();
		int	xI = pColumnName.indexOf(".");
		if (xI > 0){
			return pColumnName.substring(xI + 1);
		}else{
			return pColumnName;
		}
	}
	
	@SuppressWarnings("unchecked")
	@Override
	protected final synchronized <A> List<A> pvGetList(boolean pReturnListDataModel) throws DBSIOException {
		//Executa a Select para recuperar os registros
		
		if (pReturnListDataModel &&
			wDataModelClass == null){
			return null;
		}

		List<DBSRow>			xListRow = new ArrayList<DBSRow>();
		List<DataModelClass>	xListDataModel = new ArrayList<DataModelClass>();
		
		//Cria nova linha
		DBSRow 		xColumns = null;

		//objeto com base no DataModel
		DataModelClass xDataModel = null;
		//Popula o list com todos os registros do resultset
		if (pReturnListDataModel){
			xListDataModel.clear();
		}else{
			xListRow.clear();
		}
		//Loop de todos os registros do resulset
		moveBeforeFirstRow();
		while (moveNextRow()){
			if (pReturnListDataModel){
				//Cria novo objeto com base no DataModel
				xDataModel = this.createDataModel();
			}else{
				//Cria nova linha
				xColumns = new DBSRow();
			}
			
//			//Recuper o conteúdo de todas as colunas do registro corrente
			for (int x=1; x< getColumns().size(); x++){
				if (!pReturnListDataModel){
					xColumns.MergeColumn(getColumn(x).getColumnName(),
										 getColumn(x).getDataType(),
										 getColumn(x).getDisplaySize(), 
										 null);
					//Copia o valor para a coluna da linha
					xColumns.setValue(getColumn(x).getColumnName(), pvGetResultDataModelValueConvertedToDataType(getColumn(x).getColumnName(), getColumn(x).getDataType()));
				}
				//Adiciona a coluna a linha
				if (xDataModel!=null){ 
					//Copia o valor para o respectivo atributo no DataModel(Se houver)
					pvSetDataModelValue(xDataModel, getColumn(x).getColumnName(), pvGetResultDataModelValueConvertedToDataType(getColumn(x).getColumnName(), getColumn(x).getDataType()));
				}					
			}

			//Adiciona linha como DataModel
			if (pReturnListDataModel){
				xListDataModel.add(xDataModel);
			}else{
				//Adiciona linha como DBSRow
				xListRow.add(xColumns);
			}
		}
		if (!moveFirstRow()) {
			moveBeforeFirstRow();
		}
		if (pReturnListDataModel){
			return (List<A>) xListDataModel;
		}else{
			//Adiciona linha como DBSRow
			return (List<A>) xListRow;
		}
	}


	//=========================================================================================================
	//Overrides
	//=========================================================================================================

	@Override
	public void beforeOpen(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterOpen(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeInsert(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}
	
	@Override
	public void afterInsert(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeRead(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterRead(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeUpdate(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
		
	}

	@Override
	public void afterUpdate(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}
	
	@Override
	public void beforeMerge(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterMerge(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeDelete(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterDelete(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeMove(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterMove(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void beforeClose(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

	@Override
	public void afterClose(DBSDAOEvent pEvent) {
		// Manter vazio. Quem extender esta classe fica responsável de sobreescrever este métodos, caso precise
	}

}
