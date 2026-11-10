"""Public provenance replay is explicitly synthetic, never a claimed live query."""
import unittest
from runtime import Fixture

class CitationReplayFixtureTests(unittest.TestCase):
    def test_actual_recorded_public_answer_is_labelled_replay(self):
        fixture=Fixture.__new__(Fixture)
        fixture.provider_calls=0
        answer=fixture.fixture_provider(tools=[],messages=[{'role':'user','content':'Display retained sample [fixture:links]'}])
        text=answer.choices[0].message.content
        self.assertIn('SYNTHETIC QA',text)
        self.assertIn('replay',text)
        self.assertIn('no new model or search call',text)
        self.assertIn('Page read: https://www.fermopoint.it/',text)
        self.assertEqual(fixture.provider_calls,1)

if __name__=='__main__':unittest.main()
