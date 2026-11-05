package io.github.kkw855.settlement.core.message;

import java.util.List;

/** SQL은 resources/mapper/MessageLogMapper.xml에 있다. */
public interface MessageLogMapper {

  void insert(MessageLog log);

  List<MessageLog> findByTxnId(String txnId);
}
