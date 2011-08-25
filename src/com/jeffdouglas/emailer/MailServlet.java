package com.jeffdouglas.emailer;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Properties;
import java.util.logging.Logger;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.google.appengine.api.xmpp.JID;
import com.google.appengine.api.xmpp.MessageBuilder;
import com.google.appengine.api.xmpp.SendResponse;
import com.google.appengine.api.xmpp.XMPPService;
import com.google.appengine.api.xmpp.XMPPServiceFactory;
import com.sforce.soap.partner.PartnerConnection;
import com.sforce.soap.partner.QueryResult;
import com.sforce.soap.partner.sobject.SObject;
import com.sforce.ws.ConnectionException;

/**
 * MailServlet.java - a simple, schedulable servlet for sending mail
 * with salesforce.com
 * @author Jeff Douglas
 * @version 1.0
 * @see http://code.google.com/appengine/docs/java/mail/overview.html
 * for more details on using Mail with App Engine
 */

@SuppressWarnings("serial")
public class MailServlet extends HttpServlet {
  
    private static final Logger logger = Logger.getLogger(ConnectionManager.class.getName());
    private String jabberRecipient = "jeffdonthemic@gmail.com";

    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        
        resp.setContentType("text/html");
        String mailerMsg = "No contact found to email!!";
        QueryResult result = null;
        
        // get a reference to the salesforce connection
        PartnerConnection connection = ConnectionManager.getConnectionManager().getConnection();
        
        try {
            // query for contacts based upon some criteria -- emailNotSent boolean
            result = connection.query("Select Id, FirstName, LastName, Email " +
            		"FROM Contact Where Email = 'jeff@jeffdouglas.com' Limit 1");
        } catch (ConnectionException e) {
            e.printStackTrace();
            logger.severe(e.getCause().toString());
        } catch (NullPointerException npe) {
            npe.printStackTrace();
            logger.severe(npe.getCause().toString());
        }
        
        // if records were returned then send out email
        if (result != null) {
          
          for (SObject contact : result.getRecords()) {
            
            // construct the 'name' for the email recipient
            String contactName = contact.getField("FirstName").toString() + " " + 
              contact.getField("LastName").toString();
            
            logger.info("Sending emil to " + contactName + " at " + contact.getField("Email").toString());
            
            /// send the email
            mailerMsg = sendMail(contact.getField("Email").toString(), contactName);
            
            // send a jabber notification of the status
            sendJabberNotification(jabberRecipient, mailerMsg);
            
          }
          
          // TODO - make a call back into salesforce and update these records
          // as having their emails sent. Implementation is up to you.
          
        } else {
          logger.warning("No results returned from salesforce");
        }

        resp.getOutputStream().println(mailerMsg);

    }  
    
    /**  
     * Sends an email
     * @param toAddress the email address of the recipient
     * @param toName the name that appears for the recipeint in their email client  
     * @return A String representing the status of the email sent  
     */  
    private String sendMail(String toAddress, String toName) {
      
      String msg = "Email sent successfully to " + toAddress;
      Properties props = new Properties();
      Session session = Session.getDefaultInstance(props, null); 
            
      String messageBody = "This is the body of my email";
      
      try {
          
          Message emailMessage = new MimeMessage(session);
          //  must be the email address of an administrator for the application. see docs
          emailMessage.setFrom(new InternetAddress("jeffdonthemic@gmail.com","Jeff Douglas"));
          emailMessage.addRecipient(Message.RecipientType.TO, new InternetAddress(toAddress, toName));
          emailMessage.setSubject("My Email Subject");
          emailMessage.setText(messageBody);
          Transport.send(emailMessage);
          
      } catch (AddressException e) {
          msg = e.toString();
      } catch (MessagingException e) {
          msg = e.toString();
      } catch (UnsupportedEncodingException e) {
          msg = e.toString();
      }
      
      return msg;
    }
    
    /**  
     * Sends a message to any XMPP-compatible chat messaging service (google talk). 
     * See http://code.google.com/appengine/docs/java/xmpp/overview.html
     * for more detils
     * @param recipient the jid of the jabber recipient of the notification
     * @param msgBody the body of the message to be sent  
     */  
    private void sendJabberNotification(String recipient, String msgBody) {
      
      JID jid = new JID(recipient);
      
      com.google.appengine.api.xmpp.Message msg = new MessageBuilder()
          .withRecipientJids(jid)
          .withBody(msgBody)
          .build();
      
      boolean messageSent = false;
      XMPPService xmpp = XMPPServiceFactory.getXMPPService();
      
      if (xmpp.getPresence(jid).isAvailable()) {
          SendResponse status = xmpp.sendMessage(msg);
          messageSent = (status.getStatusMap().get(jid) == SendResponse.Status.SUCCESS);
      }
      
      logger.info("Jabber notifiation sent: " + messageSent);
      
    }
    
}
