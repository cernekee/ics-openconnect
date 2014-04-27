#!/usr/bin/perl -w

# faq.pl:
# translate faq_text from arrays.xml into bbcode for posting to the XDA thread

use strict;
use XML::LibXML;

my $filename = "res/values/arrays.xml";

sub translate($)
{
	my $in = $_[0];
	$in =~ s|\\(['"])|$1|g;
	$in =~ s|\\n|\n|g;
	$in =~ s|\[(.+?)\]\((\S+?)\)|[url="$2"]$1\[/url]|g;
	return $in;

}

#
# MAIN
#

my $parser = XML::LibXML->new();
my $xmldoc = $parser->parse_file($filename) or die;
my $root = $xmldoc->documentElement();
my $faq = $root->findnodes("/resources/string-array[\@name='faq_text']/item");

if (@$faq == 0) {
	die "can't find faq_text";
}

for (my $i = 0; $i < @$faq; $i += 2) {
	my $q = translate(@$faq[$i]->textContent);
	my $a = translate(@$faq[$i+1]->textContent);

	if ($i > 0) {
		print "\n\n";
	}

	print "[b]Q: $q"."[/b]\n\n";
	print "A: $a\n";
}

exit 0;
