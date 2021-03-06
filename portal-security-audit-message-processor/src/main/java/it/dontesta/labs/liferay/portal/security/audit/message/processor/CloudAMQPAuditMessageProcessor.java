/**
 * Copyright 2018 Antonio Musarra's Blog - https://www.dontesta.it
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:

 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.

 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package it.dontesta.labs.liferay.portal.security.audit.message.processor;

import com.liferay.portal.configuration.metatype.bnd.util.ConfigurableUtil;
import com.liferay.portal.kernel.audit.AuditMessage;
import com.liferay.portal.kernel.log.Log;
import com.liferay.portal.kernel.log.LogFactoryUtil;
import com.liferay.portal.kernel.util.StringPool;
import com.liferay.portal.security.audit.AuditMessageProcessor;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import it.dontesta.labs.liferay.portal.security.audit.message.processor.configuration.CloudAMQPAuditMessageProcessorConfiguration;

import java.util.Map;

import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Modified;

/**
 * @author Antonio Musarra
 */
@Component(
	configurationPid = "it.dontesta.labs.liferay.portal.security.audit.message.processor.configuration.CloudAMQPAuditMessageProcessorConfiguration",
	immediate = true, property = "eventTypes=" + StringPool.STAR,
	service = AuditMessageProcessor.class
)
public class CloudAMQPAuditMessageProcessor implements AuditMessageProcessor {

	@Override
	public void process(AuditMessage auditMessage) {
		try {
			doProcess(auditMessage);
		}
		catch (Exception e) {
			_log.fatal("Unable to process audit message " + auditMessage, e);
		}
	}

	@Activate
	@Modified
	protected void activate(Map<String, Object> properties) {
		_cloudAMQPAuditMessageProcessorConfiguration =
			ConfigurableUtil.createConfigurable(
				CloudAMQPAuditMessageProcessorConfiguration.class, properties);

		if (_log.isInfoEnabled()) {
			_log.info(
				"Cloud AMQP Audit Message Processor enabled: " +
				_cloudAMQPAuditMessageProcessorConfiguration.enabled());
		}
	}

	protected void doProcess(AuditMessage auditMessage) throws Exception {
		if (_cloudAMQPAuditMessageProcessorConfiguration.enabled()) {
			if (_log.isDebugEnabled()) {
				_log.debug(
					"Cloud AMQP Audit Message processor processing " +
					"this Audit Message => " + auditMessage.toJSONObject());
			}

			try {
				if (_log.isDebugEnabled()) {
					_log.debug("Try to connect " + _buildAMQPURI() + "...");
				}

				ConnectionFactory factory = new ConnectionFactory();

				factory.setUri(_buildAMQPURI());

				//Recommended settings
				factory.setRequestedHeartbeat(30);
				factory.setConnectionTimeout(30000);

				//durable - RabbitMQ will never lose the queue if a crash occurs
				boolean durable = true;

				//exclusive - if queue only will be used by one connection
				boolean exclusive = false;

				//autodelete - queue is deleted when last consumer unsubscribes
				boolean autoDelete = true;

				Connection connection = factory.newConnection();

				Channel channel = connection.createChannel();

				if (_log.isDebugEnabled()) {
					_log.debug("Try to connect " + _buildAMQPURI() + "...[OK]");
				}

				channel.queueDeclare(
					_cloudAMQPAuditMessageProcessorConfiguration.queueName(),
					durable, exclusive, autoDelete, null);

				channel.basicPublish("",
					_cloudAMQPAuditMessageProcessorConfiguration.queueName(),
					null,
					auditMessage.toJSONObject().toString().getBytes("UTF-8"));

				if (_log.isInfoEnabled()) {
					_log.info(
					"Message Audit processed and published on " +
					_cloudAMQPAuditMessageProcessorConfiguration.queueName() +
					" Cloud AMQP queue. Details {" +
					connection.getClientProperties().toString() +
					"}");
				}

				channel.close();
				connection.close();
			}
			catch (Exception e) {
				if (_log.isWarnEnabled()) {
					_log.warn(
						"Send Message Audit to Cloud AMQP Queue failed.", e);
				}
			}
		}
	}

	/**
	 * Build Cloud AMQP URI
	 *
	 * @return Cloud AMQP URI
	 */
	private String _buildAMQPURI() {
		StringBuilder sb = new StringBuilder();

		sb.append("amqp://");
		sb.append(_cloudAMQPAuditMessageProcessorConfiguration.userName());
		sb.append(":");
		sb.append(_cloudAMQPAuditMessageProcessorConfiguration.password());
		sb.append("@");
		sb.append(_cloudAMQPAuditMessageProcessorConfiguration.serverAddress());
		sb.append("/");
		sb.append(_cloudAMQPAuditMessageProcessorConfiguration.userName());

		return sb.toString();
	}

	private static final Log _log = LogFactoryUtil.getLog(
		CloudAMQPAuditMessageProcessor.class);

	private volatile CloudAMQPAuditMessageProcessorConfiguration
		_cloudAMQPAuditMessageProcessorConfiguration;

}