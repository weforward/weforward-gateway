/**
 * Copyright (c) 2019,2020 honintech
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the “Software”), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 * 
 */
package cn.weforward.gateway.plugin.alarm;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import cn.weforward.common.util.StringUtil;
import cn.weforward.gateway.GatewayNode;
import cn.weforward.gateway.ServiceInstance;
import cn.weforward.gateway.ServiceListener;
import cn.weforward.gateway.distribute.GatewayNodeListener;
import cn.weforward.gateway.distribute.GatewayNodes;
import cn.weforward.gateway.plugin.GatewayNodesAware;

/**
 * 邮件报警
 * 
 * @author daibo
 *
 */
public class EmailAlarmer implements ServiceListener, GatewayNodeListener, GatewayNodesAware {
	/** 日志 */
	private static final Logger _Logger = LoggerFactory.getLogger(EmailAlarmer.class);
	/** 当前网关节点 */
	protected GatewayNode m_GatewayNode;
	/** 发送邮件的SMTP服务器及帐号密码等 */
	protected String m_SmtpHost;
	/** 邮件用户名 */
	protected String m_SmtpUsername;
	/** 邮件密码 */
	protected String m_SmtpPassword;
	/** SMTP 30秒发送超时 */
	protected int m_SmtpTimeout = 30 * 1000;
	/** 报警接收者 */
	protected List<String> m_Receivers = Collections.emptyList();
	/** 邮件会话 */
	private Session m_SmtpSession;

	public EmailAlarmer(String smtpHost, String smtpUsername, String smtpPassword) {
		m_SmtpHost = smtpHost;
		m_SmtpUsername = smtpUsername;
		m_SmtpPassword = smtpPassword;
	}

	@Override
	public void setGatewayNodes(GatewayNodes nodes) {
		m_GatewayNode = nodes.getSelfNode();
	}

	public void setSmtpHost(String host) {
		m_SmtpHost = host;
	}

	public void setSmtpUsername(String username) {
		m_SmtpUsername = username;
	}

	public void setSmtpPassword(String password) {
		m_SmtpPassword = password;
	}

	public void setSmtpTimeout(int timeout) {
		m_SmtpTimeout = timeout;
	}

	public void setReceiver(String receivers) {
		if (StringUtil.isEmpty(receivers)) {
			m_Receivers = Collections.emptyList();
		} else {
			m_Receivers = Arrays.asList(receivers.split(";"));
		}
	}

	/** 创建会话 */
	protected Session getSmtpSession() {
		if (null == m_SmtpSession && null != m_SmtpHost) {
			// properties 参考
			// https://javamail.java.net/nonav/docs/api/com/sun/mail/smtp/package-summary.html
			Properties props = new Properties();
			// 设置mail服务器
			props.put("mail.smtp.host", m_SmtpHost);
			props.put("mail.smtp.auth", "true");
			props.put("mail.smtp.timeout", String.valueOf(m_SmtpTimeout)); // 设置SMTP超时，默认是永不
			// Get session
			m_SmtpSession = Session.getDefaultInstance(props);
			// watch the mail commands go by to the mail server
			m_SmtpSession.setDebug(false);
		}
		return m_SmtpSession;
	}

	@Override
	public void onServiceRegister(ServiceInstance service, boolean foreign) {

	}

	@Override
	public void onServiceUnregister(ServiceInstance service, boolean foreign) {

	}

	@Override
	public void onServiceTimeout(ServiceInstance service) {
		sendMail(service.getName() + "(" + service.getNo() + ")心跳超时", service);
	}

	@Override
	public void onServiceUnavailable(ServiceInstance service) {
		sendMail(service.getName() + "(" + service.getNo() + ")服务不可用", service);
	}

	@Override
	public void onServiceOverload(ServiceInstance service) {
		sendMail(service.getName() + "(" + service.getNo() + ")服务过载", service);
	}

	@Override
	public void onGatewayNodeLost(GatewayNode node) {
		sendMail("网关(" + node.getId() + ")心跳超时", node);
	}

	private void sendMail(String subject, ServiceInstance service) {
		subject = "，在网关(" + m_GatewayNode.getId() + ")上";
		String content = toContent(service);
		try {
			sendMail(subject, content);
		} catch (IOException e) {
			_Logger.warn("发送邮件异常,主题:{},内容:{}", subject, content, e);
		}
	}

	private static String toContent(ServiceInstance service) {
		StringBuilder sb = new StringBuilder();
		sb.append("服务名:").append(service.getName()).append("\n");
		sb.append("编号:").append(service.getNo()).append("\n");
		sb.append("版本号:").append(service.getVersion()).append("\n");
		sb.append("构建版本号 :").append(service.getBuildVersion()).append("\n");
		sb.append("备注:").append(StringUtil.toString(service.getNote())).append("\n");
		return sb.toString();
	}

	private void sendMail(String subject, GatewayNode node) {
		subject = "，在网关(" + m_GatewayNode.getId() + ")上";
		StringBuilder content = new StringBuilder();
		content.append("主机名:").append(node.getHostName()).append("\n");
		content.append("端口:").append(node.getPort()).append("\n");
		try {
			sendMail(subject, content.toString());
		} catch (IOException e) {
			_Logger.warn("发送邮件异常,主题:{},内容:{}", subject, content, e);
		}
	}

	private void sendMail(String subject, String content) throws IOException {
		for (String r : m_Receivers) {
			sendMail(r, subject, content);
		}
	}

	private void sendMail(String to, String subject, String content) throws IOException {
		String from = m_SmtpUsername;
		SendMail s = sendMail();
		MimeMessage message = s.getMimeMessage();
		try {
			message.setFrom(toAddress(from));
			message.addRecipient(Message.RecipientType.TO, toAddress(to));
			message.setSubject(subject);
			// 创建多重消息
			Multipart multipart = new MimeMultipart();
			BodyPart partmessage = new MimeBodyPart();
			partmessage.setText(content);
			multipart.addBodyPart(partmessage);
			message.setContent(multipart);
			message.saveChanges();
		} catch (MessagingException e) {
			throw new IOException(e);
		}
		s.send();
	}

	/**
	 * 转换地址
	 * 
	 * @param address
	 * @return
	 * @throws UnsupportedEncodingException
	 * @throws AddressException
	 */
	private static InternetAddress toAddress(String address) throws UnsupportedEncodingException, AddressException {
		// 首先找到“<>”部分
		int begin = address.indexOf('<');
		if (begin > 0 && address.charAt(address.length() - 1) == '>') {
			return new InternetAddress(address.substring(begin + 1, address.length() - 1), address.substring(0, begin),
					"utf-8");
		}
		return new InternetAddress(address);
	}

	private SendMail sendMail() {
		return new SendMailImpl();
	}

	/**
	 * 提供更多选项的邮件发送接口
	 * 
	 * @author daibo
	 * 
	 */
	public interface SendMail {
		/**
		 * 取得（创建）邮件MIME格式的内容
		 */
		MimeMessage getMimeMessage();

		/**
		 * 发送MIME内容的邮件
		 */
		void send() throws IOException;
	}

	/**
	 * SendMail简单的实现
	 * 
	 * @author daibo
	 * 
	 */
	class SendMailImpl implements SendMail {
		MimeMessage m_MimeMessage;

		@Override
		public MimeMessage getMimeMessage() {
			if (null == m_MimeMessage) {
				m_MimeMessage = new MimeMessage(m_SmtpSession);
			}
			return m_MimeMessage;
		}

		@Override
		public void send() throws IOException {
			Transport transport;
			try {
				transport = getSmtpSession().getTransport("smtp");
				transport.connect(m_SmtpHost, m_SmtpUsername, m_SmtpPassword);
				transport.sendMessage(m_MimeMessage, m_MimeMessage.getAllRecipients());
				transport.close();
			} catch (MessagingException e) {
				throw new IOException(e);
			}
			if (_Logger.isTraceEnabled()) {
				try {
					Address[] as;
					as = m_MimeMessage.getFrom();
					StringBuilder sb = new StringBuilder("SendMail# ");
					if (null != as && as.length > 0) {
						sb.append(as[0].toString());
					}
					sb.append(" => ");
					as = m_MimeMessage.getRecipients(Message.RecipientType.TO);
					if (null != as && as.length > 0) {
						sb.append(as[0].toString());
						for (int i = 1; i < as.length; i++) {
							sb.append(';').append(as[i].toString());
						}
					}
					_Logger.trace(sb.toString());
				} catch (MessagingException e) {
					_Logger.warn(e.toString(), e);
				}
			}
		}
	}

}
