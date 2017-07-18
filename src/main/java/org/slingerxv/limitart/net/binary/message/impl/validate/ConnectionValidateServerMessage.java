package org.slingerxv.limitart.net.binary.message.impl.validate;

import org.slingerxv.limitart.net.binary.message.Message;
import org.slingerxv.limitart.net.binary.message.constant.InnerMessageEnum;

public class ConnectionValidateServerMessage extends Message {
	public String validateStr;

	@Override
	public short getMessageId() {
		return InnerMessageEnum.ConnectionValidateServerMessage.getValue();
	}
}
