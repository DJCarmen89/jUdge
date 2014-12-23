package de.fellmann.judge.competition.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;

import de.fellmann.judge.Place;
import de.fellmann.judge.competition.data.Competition;
import de.fellmann.judge.competition.data.Competitor;
import de.fellmann.judge.competition.data.CompetitorJudgeKey;
import de.fellmann.judge.competition.data.Dance;
import de.fellmann.judge.competition.data.DanceCompetitorJudgeKey;
import de.fellmann.judge.competition.data.DanceCompetitorKey;
import de.fellmann.judge.competition.data.FinalResultData;
import de.fellmann.judge.competition.data.Judge;
import de.fellmann.judge.competition.data.ManualResultData;
import de.fellmann.judge.competition.data.QualificationResultData;
import de.fellmann.judge.competition.data.ResultData;
import de.fellmann.judge.competition.data.Round;
import de.fellmann.judge.skating.Calculator;
import de.fellmann.judge.skating.JudgementForFinal;

public class ResultProvider
{
	public ResultProvider()
	{
	}

	public RoundResult calculate(Competition competition,
			Round round,
			ArrayList<Competitor> preQualified,
			ArrayList<Competitor> preNotQualified)
	{
		ResultData resultData = round.getResultData();
		RoundResult roundResult = null;

		switch(round.getRoundType())
		{
		case Qualification:
			roundResult = new ManualRoundResult();
			
			if (resultData instanceof ManualResultData)
			{
				ManualResultData manualResultData = (ManualResultData) resultData;

				for (Competitor competitor : preQualified)
				{
					if (Boolean.TRUE.equals(round.getDisqualified().get(
							competitor)))
					{
						roundResult.getDisqualified().add(competitor);
					}
					else if (Boolean.TRUE.equals(manualResultData
							.getQualified().get(competitor)))
					{
						roundResult.getQualified().add(competitor);
					}
					else
					{
						roundResult.getNotQualified().add(competitor);
					}
				}

				int firstPlaceOffset = preQualified.size() - roundResult.getDisqualified().size()
						- roundResult.getQualified().size();

				for (Competitor competitor : roundResult.getNotQualified())
				{
					Place place = manualResultData.getPlace().get(competitor);
					if (place != null)
					{
						roundResult.getPlace().put(competitor, place.getWithOffset(firstPlaceOffset));
					}
				}
			}
			else if (resultData instanceof QualificationResultData)
			{
				final QualificationRoundResult qualificationRoundResult = new QualificationRoundResult();
				roundResult = qualificationRoundResult;
				
				QualificationResultData qualificationResultData = (QualificationResultData) resultData;

				ArrayList<Competitor> toSort = new ArrayList<Competitor>();
				for (Competitor competitor : preQualified)
				{
					Integer sumCompetitor = qualificationResultData.getSumCompetitor().get(competitor);
					if(sumCompetitor != null)
					{
						qualificationRoundResult.getSumCompetitor().put(competitor, sumCompetitor);
					}
					
					if(!qualificationRoundResult.getSumCompetitor().containsKey(competitor))
					{
						for(Judge j : competition.getJudges())
						{
							Integer sumCompetitorJudge = qualificationResultData.getSumCompetitorJudge().get(new CompetitorJudgeKey(competitor, j));
							if(sumCompetitorJudge != null)
							{
								qualificationRoundResult.getSumCompetitorJudge().put(new CompetitorJudgeKey(competitor, j), sumCompetitorJudge);
								putOrAdd(qualificationRoundResult.getSumCompetitor(), competitor, sumCompetitorJudge);
							}
						}
						if(sumCompetitor != null) {
							qualificationRoundResult.getSumCompetitor().put(competitor, sumCompetitor);
						}
					}
					
					if(!qualificationRoundResult.getSumCompetitor().containsKey(competitor))
					{
						for(Dance d : competition.getDances())
						{
							Integer sumDanceCompetitor = qualificationResultData.getSumDanceCompetitor().get(new DanceCompetitorKey(d, competitor));
							if(sumDanceCompetitor != null)
							{
								qualificationRoundResult.getSumDanceCompetitor().put(new DanceCompetitorKey(d, competitor), sumDanceCompetitor);
								sumCompetitor = (sumCompetitor == null ? 0 : sumCompetitor) + sumDanceCompetitor;
							}
						}
						if(sumCompetitor != null) {
							qualificationRoundResult.getSumCompetitor().put(competitor, sumCompetitor);
						}
					}
					
					if(!qualificationRoundResult.getSumCompetitor().containsKey(competitor))
					{
						putOrAdd(qualificationRoundResult.getSumCompetitor(), competitor, 0);
						for(Dance d : competition.getDances())
						{
							for(Judge j : competition.getJudges())
							{
								Boolean cross = qualificationResultData.getCross().get(new DanceCompetitorJudgeKey(d, competitor, j));
								if(Boolean.TRUE.equals(cross))
								{
									putOrAdd(qualificationRoundResult.getSumCompetitor(), competitor, 1);
									putOrAdd(qualificationRoundResult.getSumDanceCompetitor(), new DanceCompetitorKey(d, competitor), 1);
									putOrAdd(qualificationRoundResult.getSumCompetitorJudge(), new CompetitorJudgeKey(competitor, j), 1);
								}
							}
						}
					}
					
					if (Boolean.TRUE.equals(round.getDisqualified().get(
							competitor)))
					{
						roundResult.getDisqualified().add(competitor);
					}
					else {
						sumCompetitor = qualificationRoundResult.getSumCompetitor().get(competitor);
						if(sumCompetitor != null && sumCompetitor >= qualificationResultData.getSumToQualify())
						{
							roundResult.getQualified().add(competitor);
						}
						else
						{
							roundResult.getNotQualified().add(competitor);
							toSort.add(competitor);
						}
					}
				}
				
				Collections.sort(toSort, new Comparator<Competitor>() {

					public int compare(Competitor o1, Competitor o2)
					{
						Integer o1sum = qualificationRoundResult.getSumCompetitor().get(o1);
						Integer o2sum = qualificationRoundResult.getSumCompetitor().get(o2);
						return Integer.compare(o2sum == null ? 0 : o2sum, o1sum == null ? 0 : o1sum);
					}
				});

				int placeOffset = roundResult.getQualified().size();
				int count = 1;
				for(int i=0;i<toSort.size();i+=count)
				{
					count = 1;
					Integer o1sum = qualificationRoundResult.getSumCompetitor().get(toSort.get(i));
					while(toSort.size() > i + count)
					{
						Integer o2sum = qualificationRoundResult.getSumCompetitor().get(toSort.get(i+count));
						if ((o1sum == null && o2sum == null)
								|| (o1sum != null && o1sum.equals(o2sum)))
						{
							count++;
						}
						else
						{
							break;
						}
					}
					
					for(int j=i;j<i+count;j++)
					{
						qualificationRoundResult.getPlace().put(toSort.get(j), new Place(i+placeOffset+1, i+count+placeOffset));
					}
				}
			}
			break;
		case Final:
			FinalRoundResult finalRoundResult = new FinalRoundResult();
			FinalResultData finalResultData = (FinalResultData)resultData;
			roundResult = finalRoundResult;
			
			for(Competitor competitor : preQualified)
			{
				if (Boolean.TRUE.equals(round.getDisqualified().get(
						competitor)))
				{
					roundResult.getDisqualified().add(competitor);
				}
				else
				{
					roundResult.getNotQualified().add(competitor);
				}
			}
			
			JudgementForFinal judgement = new JudgementForFinal(competition.getDances().size(), preQualified.size()-roundResult.getDisqualified().size(), competition.getJudges().size());
			
			for(int d = 0; d <competition.getDances().size();d++)
			{
				Dance dance = competition.getDances().get(d);
				for(int c = 0; c < preQualified.size(); c++)
				{
					Competitor competitor = preQualified.get(c);
					if(!Boolean.TRUE.equals(round.getDisqualified().get(
							competitor)))
					{
						for(int j=0;j<competition.getJudges().size(); j++)
						{
							Judge judge = competition.getJudges().get(j);
							final Integer mark = finalResultData.getMark().get(new DanceCompetitorJudgeKey(dance, competitor, judge));
							if(mark != null)
							{
								judgement.setMark(d, c, j, (byte)(int)mark);
							}
						}
					}
				}
			}
			
			Calculator calculator = new Calculator(judgement);
			int resultOffset = roundResult.getQualified().size();
			for(int c = 0; c < preQualified.size(); c++)
			{
				Competitor competitor = preQualified.get(c);
				if(!Boolean.TRUE.equals(round.getDisqualified().get(
						competitor)))
				{
					roundResult.getPlace().put(competitor, calculator.getResult(c).getWithOffset(resultOffset));
				}
			}
			break;
		default:
			throw new RuntimeException("Not implemented: " + round.getRoundType());
			
		}

		return roundResult;
	}

	private <T> void putOrAdd(Map<T, Integer> map, T key, int value)
	{
		Integer oldValue = map.get(key);
		if (oldValue == null)
		{
			map.put(key, value);
		}
		else
		{
			map.put(key, value + oldValue);
		}
	}
}
