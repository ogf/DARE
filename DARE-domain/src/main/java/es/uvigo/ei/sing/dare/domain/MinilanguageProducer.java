package es.uvigo.ei.sing.dare.domain;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import es.uvigo.ei.sing.stringeditor.Minilanguage;

public class MinilanguageProducer {

    protected static final Log LOG = LogFactory
            .getLog(MinilanguageProducer.class);

    private BlockingQueue<Minilanguage> queue;

    public MinilanguageProducer(int capacity) {
        this.queue = new LinkedBlockingQueue<Minilanguage>(capacity);
        Thread producer = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        queue.put(new Minilanguage());
                    } catch (Exception e) {
                        LOG.error("unexpected error", e);
                    }
                }
            }
        });
        producer.setDaemon(true);
        producer.start();
    }

    public Minilanguage newMinilanguage() {
        try {
            return queue.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

}
