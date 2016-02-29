package org.mifos.module.stellar.repository;

import org.mifos.module.stellar.persistencedomain.StellarPaymentEventPersistency;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StellarPaymentEventRepository extends
    CrudRepository<StellarPaymentEventPersistency, Long> {
}
