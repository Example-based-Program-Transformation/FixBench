package br.com.dbsoft.mail;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Authenticator;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.SendFailedException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;
import javax.mail.Message.RecipientType;

import org.apache.log4j.Logger;

import br.com.dbsoft.core.DBSSDK.CONTENT_TYPE;
import br.com.dbsoft.core.DBSSDK.ENCODE;
import br.com.dbsoft.core.DBSSDK.NETWORK.PROTOCOL;
import br.com.dbsoft.util.DBSFile;
import br.com.dbsoft.util.DBSEmail;
import br.com.dbsoft.util.DBSObject;


public class DBSEmailSend {

	protected static Logger					wLogger = Logger.getLogger(DBSEmailSend.class);
	
	private String 		wHostUserName;
	private String 		wHostPassword;
	private String 		wHost;
	private String 		wHostPort = "587";
	private PROTOCOL 	wProtocol;
	private String		wProtocolString;
	private List<DBSEmailAddress> wInvalidEmails = new ArrayList<DBSEmailAddress>();
	

	public DBSEmailSend(){
		setProtocol(null);
	}
	
	//================================================================================================================
	/**
	 * Conta do usuário no servidor de email
	 * @return
	 */
	public String getHostUserName() {return wHostUserName;}
	/**
	 * Conta do usuário no servidor de email
	 * @return
	 */
	public void setHostUserName(String pHostUserName) {wHostUserName = pHostUserName;}

	/**
	 * Senha do usuário no servidor de email
	 * @return
	 */
	public String getHostPassword() {return wHostPassword;}
	/**
	 * Senha do usuário no servidor de email
	 * @return
	 */
	public void setHostPassword(String pHostPassword) {wHostPassword = pHostPassword;}

	/**
	 * Servidor SMTP de email
	 * @return
	 */
	public String getHost() {return wHost;}
	/**
	 * Servidor SMTP de email
	 * @return
	 */
	public void setHost(String pHost) {wHost = pHost;}

	/**
	 * Porta do servidor de email
	 * @return
	 */
	public String getHostPort() {return wHostPort;}
	/**
	 * Porta do servidor de email
	 * @return
	 */
	public void setHostPort(String pHostPort) {wHostPort = pHostPort;}

	/**
	 * Protocolo smtp
	 * @return
	 */
	public PROTOCOL getProtocol() {return wProtocol;}
	/**
	 * Protocolo smtp
	 * @return
	 */
	public void setProtocol(PROTOCOL pProtocol) {
		wProtocol = pProtocol;
		if (pProtocol == null){
			wProtocolString = "smtp";
		}else if (pProtocol == PROTOCOL.SSL){
			wProtocolString = "smtps";
		}
	}

	public String getProtocolString() {return wProtocolString;}
	
	/**
	 * Retorna emails inválidos
	 * @return
	 */
	public List<DBSEmailAddress> getInvalidEmails(){
		return wInvalidEmails;
	}
	//================================================================================================================
	/**
	 * Envia o email para os endereço informados.<br/>
	 * Endereço de email inválidos serão adicionados o atributo <i>invalidEmails</i>.
	 * @param pMessage
	 * @return
	 */
	public boolean send(DBSEmailMessage pMessage){
		wInvalidEmails.clear();
		if (pMessage == null
			|| DBSObject.isEmpty(pMessage.getFrom())
			|| DBSObject.isEmpty(pMessage.getTo())
			|| DBSObject.isEmpty(pMessage.getSubject())){
			return false;
		}
		Properties 		xProps = new Properties();
		Session 		xMailSession = Session.getInstance(xProps, new SMTPAuthenticator());
	    Message 		xMessage = new MimeMessage(xMailSession);
		InternetAddress xFromAddress = null;
		Transport 		xTransport;
		BodyPart 		xMessageBodyPart = new MimeBodyPart();
		Multipart 		xMultipart;

		xProps.put("mail.transport.protocol", wProtocolString);
		xProps.put("mail." + wProtocolString + ".host", wHost); 
		xProps.put("mail." + wProtocolString + ".port", wHostPort); 
		
		if (!DBSObject.isEmpty(wHostUserName)
		 && !DBSObject.isEmpty(wHostPassword)){
			xProps.put("mail." + wProtocolString + ".auth", "true");   
		}
		
		if (wProtocol != null){
			if (wProtocol == PROTOCOL.SSL){
				xProps.put("mail." + wProtocolString + ".ssl.enable", "true");
				xProps.put("mail." + wProtocolString + ".ssl.required", "true");
			} else if (wProtocol == PROTOCOL.STARTTLS){
				xProps.put("mail.smtp.starttls.enable", "true");    
		        xProps.put("mail.smtp.socketFactory.port", wHostPort);    
		        xProps.put("mail.smtp.socketFactory.fallback", "false");    
		        xProps.put("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
			} else if (wProtocol == PROTOCOL.TLS){
				xProps.put("mail.smtp.starttls.enable", "true");
			}
		}
	    
		try {
			//FROM
			xFromAddress = new InternetAddress(pMessage.getFrom().getAddress());
			xFromAddress.setPersonal(pMessage.getFrom().getName());
			xMessage.setFrom(xFromAddress);
			
			//TO ---------------------
			for (DBSEmailAddress xEmailAddress:DBSEmail.validateEmailAddress(pMessage.getTo())){ //ALBERTO: Adicionada validação de lista de E-mails. Em 26/03/2015.
				pvAddRecipient(xMessage, RecipientType.TO, xEmailAddress);
			}
			//CC ---------------------
			for (DBSEmailAddress xEmailAddress:pMessage.getCC()){
				pvAddRecipient(xMessage, RecipientType.CC, xEmailAddress);
			}
			//BCC ---------------------
			for (DBSEmailAddress xEmailAddress:pMessage.getBCC()){
				pvAddRecipient(xMessage, RecipientType.BCC, xEmailAddress);
			}
			if (xMessage.getAllRecipients() == null){
				return false;
			}
			
			//Texto da mensagem----------
			xMessage.setSubject(pMessage.getSubject());
			if (pMessage.getIsHtmlContent()){
				xMessageBodyPart.setContent(pMessage.getText(), CONTENT_TYPE.TEXT_HTML +  "; charset=" + ENCODE.UTF_8.toLowerCase());			
			}else{
				xMessageBodyPart.setText(pMessage.getText());
			}
			xMultipart = new MimeMultipart();
			xMultipart.addBodyPart(xMessageBodyPart);
			
			//Anexos---------------------
			for (String xFilename:pMessage.getAttachments()){
				xMessageBodyPart = new MimeBodyPart();
				DataSource source = new FileDataSource(xFilename);
				xMessageBodyPart.setDataHandler(new DataHandler(source));
				xMessageBodyPart.setFileName(DBSFile.getFileNameFromPath(xFilename));
				xMultipart.addBodyPart(xMessageBodyPart);
			}

			
	        //Envio-----------------------------------
	        xMessage.setContent(xMultipart);

			xTransport = xMailSession.getTransport(wProtocolString.toString());
			xTransport.connect();
			xMessage.saveChanges(); 
			xTransport.sendMessage(xMessage, xMessage.getAllRecipients());
			xTransport.close();
			return true;
		} catch (SendFailedException e) {
			wLogger.error(e);
		} catch (AddressException e) {
			wLogger.error(e);
		} catch (MessagingException e) {
			wLogger.error(e);
		} catch (UnsupportedEncodingException e) {
			wLogger.error(e);
		}
		return false;
		
	}
	
    private class SMTPAuthenticator extends Authenticator {
        @Override
		public PasswordAuthentication getPasswordAuthentication() {
           String username = wHostUserName;
           String password = wHostPassword;
           return new PasswordAuthentication(username, password);
        }
    }
    
	private void pvAddRecipient(Message pMessage, RecipientType pRecipientType, DBSEmailAddress pEmailAddress) throws MessagingException, UnsupportedEncodingException{
		if (pMessage == null 
		 || pRecipientType == null
		 || pEmailAddress == null
		 || DBSObject.isEmpty(pEmailAddress.getAddress())){
			return;
		}

		if (!DBSEmail.isValidEmailAddress(pEmailAddress.getAddress())){
			wInvalidEmails.add(pEmailAddress);
		}
		
		InternetAddress xToAddress = new InternetAddress(pEmailAddress.getAddress());
		if (DBSObject.isEmpty(pEmailAddress.getName())){
			xToAddress.setPersonal(pEmailAddress.getAddress());
		}else{
			xToAddress.setPersonal(pEmailAddress.getName());
		}
		pMessage.addRecipient(pRecipientType, xToAddress);
	}
	

	
}
