package com.annakhuseinova.springcloudstreamsexactlyonce.services;

import com.annakhuseinova.springcloudstreamsexactlyonce.bindinds.PosListenerBinding;
import com.annakhuseinova.springcloudstreamsexactlyonce.model.HadoopRecord;
import com.annakhuseinova.springcloudstreamsexactlyonce.model.Notification;
import com.annakhuseinova.springcloudstreamsexactlyonce.model.PosInvoice;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.kstream.KStream;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@EnableBinding(PosListenerBinding.class)
@RequiredArgsConstructor
public class PosListenerService {

    private final RecordBuilder recordBuilder;

    // This whole method (this listener) will be regarded as a single transaction that will be rollback in case of some failure
    // in course of execution. But this transaction is only for the Kafka operations, not kafka operations are
    // not part of the transaction. If you need, for example, to write to database in this method, use Kafka Connect API
    @StreamListener("pos-input-channel")
    public void process(KStream<String, PosInvoice> input){
        KStream<String, HadoopRecord> hadoopRecordKStream = input.mapValues(recordBuilder::getMaskedInvoice)
                .flatMapValues(recordBuilder::getHadoopRecords);
        KStream<String, Notification> notificationKStream = input.filter((key, value)-> value.getCustomerType()
                .equalsIgnoreCase("PRIME")).mapValues(recordBuilder::getNotification);
        hadoopRecordKStream.foreach((key, value)->  log.info(String.format("Hadoop Record:- Key: %s, Value: %s", key, value)));
        notificationKStream.foreach((key, value)-> log.info(String.format("Notification:- Key: %s, Value: %s", key, value)));
        // Here we use Kafka Streams API to send data directly to topics without creating output channels
        hadoopRecordKStream.to("hadoop-sink-topic");
        hadoopRecordKStream.to("loyalty-topic");
    }
}
