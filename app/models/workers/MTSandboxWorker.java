package models.workers;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("MTSandbox")
public class MTSandboxWorker extends MTWorker {

}
