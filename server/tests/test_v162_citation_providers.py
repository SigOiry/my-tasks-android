import unittest
from citations import saved_metadata
from email_providers import source_id


class SavedProviderCitationTests(unittest.TestCase):
    def test_real_nantes_reference_format_retains_authenticated_email_citation(self):
        ref='nantes-imap://INBOX;UIDVALIDITY=123;UID=45;Message-ID=<review@example.test>'
        meta=saved_metadata({'id':'synthetic-task','title':'Review','source_references':[ref]})
        emails=[s for s in meta['sources'] if s['kind']=='saved_email']
        self.assertEqual(len(emails),1)
        self.assertEqual(emails[0]['resource_id'],source_id('synthetic-task',ref))
        self.assertNotIn('url',emails[0])
        self.assertEqual(emails[0]['verification'],'saved_context')
