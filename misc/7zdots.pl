#!/usr/bin/perl -w

use strict;

my $skipped = 0;
while (<>) {
	if (m/^(Extracting)|(Skipping)/) {
		$skipped++;
		if ($skipped >= 25) {
			print ".";
			flush STDOUT;
			$skipped = 0;
		}
	} else {
		print "\n" if $skipped;
		print "$_";
		$skipped = 0;
	}
}
print "\n" if $skipped;

exit 0;
