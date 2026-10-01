package com.androiddrop.transfer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ResumeRangeTests {

	@Test
	void onlyRangesThatRunToTheEndResume() {
		assertThat(TransferController.resumeFrom("bytes=100-", 1000)).isEqualTo(100);
		assertThat(TransferController.resumeFrom("bytes=100-999", 1000)).isEqualTo(100);
		assertThat(TransferController.resumeFrom(null, 1000)).isZero();
		// Anything else gets the whole file, which a server is always allowed to answer with.
		assertThat(TransferController.resumeFrom("bytes=100-200", 1000)).isZero();
		assertThat(TransferController.resumeFrom("bytes=-100", 1000)).isZero();
		assertThat(TransferController.resumeFrom("bytes=0-10,20-30", 1000)).isZero();
		assertThat(TransferController.resumeFrom("bytes=1000-", 1000)).isZero();
		assertThat(TransferController.resumeFrom("bytes=99999999999999999999-", 1000)).isZero();
	}

}
