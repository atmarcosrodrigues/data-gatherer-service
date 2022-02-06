package com.silibrina.tecnova.opendata.gatherer.messenger;

import com.silibrina.tecnova.commons.messenger.Message;
import com.silibrina.tecnova.commons.messenger.MessageStatus;
import com.silibrina.tecnova.commons.messenger.producer.ProducerService;

import java.io.IOException;

public class DummyProducerService implements ProducerService {
    @Override
    public void close() { }

    @Override
    public int publish(Message message) throws IOException, InterruptedException { return MessageStatus.UNDEFINED_STATUS.status; }
}
