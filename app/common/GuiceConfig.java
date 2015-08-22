package common;

import models.workers.GeneralSingleWorker;
import models.workers.JatosWorker;
import models.workers.MTWorker;
import models.workers.PersonalMultipleWorker;
import models.workers.PersonalSingleWorker;
import publix.services.IStudyAuthorisation;
import publix.services.PublixUtils;
import publix.services.general_single.GeneralSinglePublixUtils;
import publix.services.general_single.GeneralSingleStudyAuthorisation;
import publix.services.jatos.JatosPublixUtils;
import publix.services.jatos.JatosStudyAuthorisation;
import publix.services.mt.MTPublixUtils;
import publix.services.mt.MTStudyAuthorisation;
import publix.services.personal_multiple.PersonalMultiplePublixUtils;
import publix.services.personal_multiple.PersonalMultipleStudyAuthorisation;
import publix.services.personal_single.PersonalSinglePublixUtils;
import publix.services.personal_single.PersonalSingleStudyAuthorisation;

import com.google.inject.AbstractModule;
import com.google.inject.TypeLiteral;

public class GuiceConfig extends AbstractModule {
	@Override
	protected void configure() {
		bind(new TypeLiteral<IStudyAuthorisation<GeneralSingleWorker>>() {
		}).to(GeneralSingleStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<JatosWorker>>() {
		}).to(JatosStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<MTWorker>>() {
		}).to(MTStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<PersonalMultipleWorker>>() {
		}).to(PersonalMultipleStudyAuthorisation.class);
		bind(new TypeLiteral<IStudyAuthorisation<PersonalSingleWorker>>() {
		}).to(PersonalSingleStudyAuthorisation.class);

		bind(new TypeLiteral<PublixUtils<GeneralSingleWorker>>() {
		}).to(GeneralSinglePublixUtils.class);
		bind(new TypeLiteral<PublixUtils<JatosWorker>>() {
		}).to(JatosPublixUtils.class);
		bind(new TypeLiteral<PublixUtils<MTWorker>>() {
		}).to(MTPublixUtils.class);
		bind(new TypeLiteral<PublixUtils<PersonalMultipleWorker>>() {
		}).to(PersonalMultiplePublixUtils.class);
		bind(new TypeLiteral<PublixUtils<PersonalSingleWorker>>() {
		}).to(PersonalSinglePublixUtils.class);
	}
}
